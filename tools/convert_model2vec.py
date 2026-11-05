"""Convert a Model2Vec model (safetensors + tokenizer.json) into the compact asset format read by
dev.patrickgold.florisboard.ime.journal.vector.StaticEmbedder, and emit reference vectors for tests.

Asset layout (little endian):
  magic  b"M2V1"
  int32  vocab_size
  int32  dim
  float32[vocab_size]       per-row dequantization scale
  int8[vocab_size * dim]    row-major quantized embeddings
vocab.txt: one token per line, line index == token id.

Usage: python -I convert_model2vec.py <model_dir> <asset_out_dir> <test_resources_out_dir>
"""
import json
import struct
import sys
from pathlib import Path

import numpy as np
from tokenizers import Tokenizer

SAMPLES = [
    "best suv under 50k for a family of 5",
    "Which SUV has the lowest maintenance cost?",
    "plan a 10 day itinerary for Japan in April",
    "How do I negotiate my salary offer?",
    "HubSpot vs Salesforce for a 10-person startup",
    "Ünïcödé café naïve résumé",
    "what's the weather like in Tokyo?? 🌸",
]


def load_safetensors(path: Path) -> np.ndarray:
    with open(path, "rb") as f:
        header_len = struct.unpack("<Q", f.read(8))[0]
        header = json.loads(f.read(header_len))
        data = f.read()
    tensors = {k: v for k, v in header.items() if k != "__metadata__"}
    if len(tensors) != 1:
        raise SystemExit(f"expected exactly one tensor, got {list(tensors)}")
    (name, info), = tensors.items()
    if info["dtype"] != "F32":
        raise SystemExit(f"unsupported dtype {info['dtype']}")
    start, end = info["data_offsets"]
    arr = np.frombuffer(data[start:end], dtype="<f4").reshape(info["shape"])
    print(f"tensor {name} shape={arr.shape}")
    return arr


def encode_reference(tokenizer: Tokenizer, emb: np.ndarray, unk_id: int, text: str) -> np.ndarray:
    ids = [i for i in tokenizer.encode(text, add_special_tokens=False).ids if i != unk_id]
    if not ids:
        return np.zeros(emb.shape[1], dtype=np.float32)
    v = emb[ids].mean(axis=0)
    return v / max(np.linalg.norm(v), 1e-12)


def main() -> None:
    model_dir, asset_dir, test_dir = (Path(p) for p in sys.argv[1:4])
    asset_dir.mkdir(parents=True, exist_ok=True)
    test_dir.mkdir(parents=True, exist_ok=True)

    emb = load_safetensors(model_dir / "model.safetensors").astype(np.float32)
    tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
    vocab = tokenizer.get_vocab()
    id_to_token = [None] * len(vocab)
    for tok, i in vocab.items():
        id_to_token[i] = tok
    assert len(id_to_token) == emb.shape[0], (len(id_to_token), emb.shape)
    unk_id = vocab["[UNK]"]

    scales = np.abs(emb).max(axis=1) / 127.0
    scales[scales == 0] = 1.0
    q = np.clip(np.round(emb / scales[:, None]), -127, 127).astype(np.int8)

    with open(asset_dir / "embeddings.bin", "wb") as f:
        f.write(b"M2V1")
        f.write(struct.pack("<ii", emb.shape[0], emb.shape[1]))
        f.write(scales.astype("<f4").tobytes())
        f.write(q.tobytes())
    (asset_dir / "vocab.txt").write_text("\n".join(id_to_token) + "\n", encoding="utf-8")

    deq = q.astype(np.float32) * scales[:, None]
    refs = []
    for text in SAMPLES:
        full = encode_reference(tokenizer, emb, unk_id, text)
        quant = encode_reference(tokenizer, deq, unk_id, text)
        ids = [i for i in tokenizer.encode(text, add_special_tokens=False).ids if i != unk_id]
        print(f"cos(float,int8)={float(full @ quant):.5f}  {text!r}")
        refs.append({"text": text, "ids": ids, "vector": [round(float(x), 6) for x in quant]})
    (test_dir / "model2vec_reference.json").write_text(json.dumps(refs, ensure_ascii=False), encoding="utf-8")
    size = (asset_dir / "embeddings.bin").stat().st_size
    print(f"wrote embeddings.bin ({size / 1e6:.1f} MB), vocab.txt ({len(id_to_token)} tokens)")


if __name__ == "__main__":
    main()
