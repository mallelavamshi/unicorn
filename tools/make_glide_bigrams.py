#!/usr/bin/env python3
"""Builds the next-word table glide typing uses to pick between words with the same path ("for" and "four").

The engine's English dictionary has word frequencies but no next-word data, so this counts word pairs in the English
sentences of Tatoeba (https://tatoeba.org, CC BY 2.0 FR, credited in NOTICE) and keeps the pairs between dictionary
words. Each entry is how much more (or less) likely a word is after the previous one than on its own, in nats:

    boost(prev, word) = ln(P(word | prev) / P(word))

with absolute discounting (D = 0.75) and backoff to the unigram for pairs that were never seen.

Usage:
    curl -O https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2
    python tools/make_glide_bigrams.py eng_sentences.tsv.bz2

Writes app/src/main/assets/glide/en_next.bin. Format (big-endian), read by ai.unicorn.glide.NextWords:
    "UGN1"
    u16 W, then W words (u8 byte length + UTF-8, lower case); word 0 is "<s>", the start of a sentence
    u16 C, then C contexts sorted by word index: u16 word, s8 backoff (nats * 8), u16 n, u32 offset into the pairs
    pairs: for each context, n entries sorted by word index: u16 word, s8 boost (nats * 8)
"""
import bz2
import collections
import gzip
import math
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORDLIST = os.path.join(ROOT, "upstream", "latinime", "dictionaries", "en_wordlist.combined.gz")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "glide", "en_next.bin")

DISCOUNT = 0.75
MIN_CONTEXT_COUNT = 50      # contexts seen fewer times than this just back off to the unigram
MIN_PAIR_COUNT = 5         # single sightings are noise; they fall into the backoff mass
MAX_PAIRS_PER_CONTEXT = 800
MAX_WORDS = 65535
START = "<s>"

TOKEN = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)*|[0-9]+|[^\sA-Za-z0-9]")
CONTINUE = {",", "'"}


def dictionary_words():
    words = set()
    with gzip.open(WORDLIST, "rt", encoding="utf-8") as f:
        for line in f:
            if not line.startswith(" word="):
                continue
            fields = dict(p.split("=", 1) for p in line.strip().split(","))
            if fields.get("possibly_offensive") == "true" or fields.get("not_a_word") == "true":
                continue
            words.add(fields["word"].lower())
    return words


def sentences(path):
    opener = bz2.open if path.endswith(".bz2") else open
    with opener(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                yield parts[2]


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    vocab = dictionary_words()
    unigrams = collections.Counter()
    pairs = collections.Counter()
    for sentence in sentences(sys.argv[1]):
        prev = START
        for token in TOKEN.findall(sentence.replace("’", "'")):
            low = token.lower()
            if low in vocab:
                unigrams[low] += 1
                if prev is not None:
                    pairs[(prev, low)] += 1
                prev = low
            elif token in CONTINUE:
                continue
            elif token in ".!?":
                prev = START
            else:
                prev = None  # a number, a name we don't know, a quote: no context for the next word

    total = sum(unigrams.values())
    vocab_size = len(unigrams)

    def p_uni(word):
        return (unigrams[word] + 0.5) / (total + 0.5 * vocab_size)

    by_context = collections.defaultdict(dict)
    context_count = collections.Counter()
    distinct = collections.Counter()
    for (prev, word), count in pairs.items():
        context_count[prev] += count
        distinct[prev] += 1
        if count >= MIN_PAIR_COUNT:
            by_context[prev][word] = count

    contexts = {}
    for prev, followers in by_context.items():
        n = context_count[prev]
        if n < MIN_CONTEXT_COUNT:
            continue
        interpolation = DISCOUNT * distinct[prev] / n
        top = sorted(followers, key=lambda w: -followers[w])[:MAX_PAIRS_PER_CONTEXT]
        entries = {}
        kept_conditional = 0.0
        kept_unigram = 0.0
        for word in top:
            p = (followers[word] - DISCOUNT) / n + interpolation * p_uni(word)
            entries[word] = math.log(p / p_uni(word))
            kept_conditional += p
            kept_unigram += p_uni(word)
        # Words not kept share what probability is left, in proportion to their unigram (Katz). Pruned pairs keep
        # their mass this way, so a word rarely seen after the context isn't punished as if it never could be.
        alpha = max(1e-3, 1.0 - kept_conditional) / max(1e-3, 1.0 - kept_unigram)
        contexts[prev] = (math.log(alpha), entries)

    # Word table: <s> first, then by frequency, capped to what a u16 can address
    used = collections.Counter()
    for prev, (_, entries) in contexts.items():
        used[prev] += context_count[prev]
        for word in entries:
            used[word] += unigrams[word]
    words = [START] + [w for w, _ in used.most_common() if w != START][:MAX_WORDS - 1]
    index = {w: i for i, w in enumerate(words)}

    def q(nats):
        return max(-128, min(127, int(round(nats * 8))))

    header = bytearray(b"UGN1")
    header += struct.pack(">H", len(words))
    for w in words:
        b = w.encode("utf-8")
        header += struct.pack(">B", len(b)) + b
    table = []
    body = bytearray()
    for prev in sorted((c for c in contexts if c in index), key=lambda c: index[c]):
        backoff, entries = contexts[prev]
        kept = sorted((index[w], q(boost)) for w, boost in entries.items() if w in index)
        table.append(struct.pack(">HbHI", index[prev], q(backoff), len(kept), len(body)))
        for word_index, boost in kept:
            body += struct.pack(">Hb", word_index, boost)
    out = header + struct.pack(">H", len(table)) + b"".join(table) + body
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(out)
    print(f"{total} words read, {len(words)} in the table, {len(table)} contexts, "
          f"{len(body) // 3} pairs, {len(out) / 1024:.0f} KiB -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
