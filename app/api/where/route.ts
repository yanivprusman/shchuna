import { authorize, failure, readLatLon } from "@/lib/auth";
import { where } from "@/lib/where";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const denied = authorize(request);
  if (denied) return denied;
  const url = new URL(request.url);
  const point = readLatLon(url.searchParams.get("lat"), url.searchParams.get("lon"));
  if (!point) return Response.json({ error: "lat and lon are required" }, { status: 400 });
  try {
    const { answer: _internal, ...view } = await where(point.lat, point.lon);
    return Response.json(view);
  } catch (error) {
    return failure(error);
  }
}
