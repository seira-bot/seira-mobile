import assetLinks from "../assetlinks.json";

const ASSET_LINKS = JSON.stringify(assetLinks);

export default {
  fetch(request) {
    const { pathname } = new URL(request.url);
    if (pathname === "/.well-known/assetlinks.json") {
      return new Response(ASSET_LINKS, {
        headers: {
          "content-type": "application/json",
          "cache-control": "public, max-age=3600",
        },
      });
    }
    return Response.redirect("https://seira.bot/", 302);
  },
};
