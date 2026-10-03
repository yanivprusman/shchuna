import { authorize, failure, readLatLon } from "@/lib/auth";
import { activeSessions, stopParking } from "@/lib/cello";

export const dynamic = "force-dynamic";

/** Stop the running parking session. Body: { lat?, lon?, transactionId? }. */
export async function POST(request: Request) {
  const denied = authorize(request);
  if (denied) return denied;
  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  const point = readLatLon(body?.lat, body?.lon);
  try {
    const sessions = await activeSessions();
    const wanted = body?.transactionId != null ? sessions.filter((s) => s.transactionId === Number(body.transactionId)) : sessions;
    if (wanted.length === 0) return Response.json({ outcome: "nothing-active", message: "אין חניה פעילה" });
    if (wanted.length > 1) {
      return Response.json({ outcome: "choose-session", sessions: wanted, message: "יש יותר מחניה פעילה אחת" }, { status: 409 });
    }
    const message = await stopParking({ transactionId: wanted[0].transactionId, lat: point?.lat, lon: point?.lon });
    return Response.json({ outcome: "stopped", message, session: wanted[0] });
  } catch (error) {
    return failure(error);
  }
}
