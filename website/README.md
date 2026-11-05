# Unicorn website

Static landing page, blog and privacy page. No build step.

- Preview: `python -m http.server 8642` in this folder, then open http://localhost:8642
- Live hunches in the hero and the early-access form call the Unicorn API, which answers the origins listed in
  `SITE_ORIGINS` in `backend/wrangler.jsonc` (and any localhost port).
- Live at https://unicorn.cognovaai.com (Cloudflare static files, no code).
- Deploy: run `npx wrangler deploy` from this folder, which uses `wrangler.jsonc` here. Never run it, or
  `wrangler pages project create`, from `backend/`: that picks up the API's config instead. `.assetsignore` keeps
  this README and the config off the site. If the site gets its own domain, add it to `SITE_ORIGINS` and redeploy
  the backend.
