import { authorize, failure } from "@/lib/auth";
import { lookupStreet } from "@/lib/street";

export const dynamic = "force-dynamic";

/** GET /api/street?q=סוקולוב חולון → the street and the neighbourhoods it runs through. */
export async function GET(request: Request) {
  const denied = authorize(request);
  if (denied) return denied;
  const q = new URL(request.url).searchParams.get("q")?.trim();
  if (!q || q.length < 2) return Response.json({ error: "q is required" }, { status: 400 });
  try {
    return Response.json({ matches: await lookupStreet(q) });
  } catch (error) {
    return failure(error);
  }
}
