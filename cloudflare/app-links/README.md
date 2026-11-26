# seira.run app links

A Worker on the apex `seira.run`. It serves `/.well-known/assetlinks.json`, so
Android verifies the app's `https://*.seira.run` links and opens them in the
Seira app. Every other path redirects to `https://seira.bot/`.

`assetlinks.json` lists the SHA-256 fingerprints of the Play App Signing key
and the upload key. Change it when either key changes.

Deploy with an account that holds the `seira.run` zone:

```sh
cd cloudflare/app-links
CLOUDFLARE_ACCOUNT_ID=<account id> npx wrangler deploy
```

Check:

```sh
curl -si https://seira.run/.well-known/assetlinks.json
```
