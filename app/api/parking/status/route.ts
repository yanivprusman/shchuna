import { authorize, failure } from "@/lib/auth";
import { activeSessions } from "@/lib/cello";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const denied = authorize(request);
  if (denied) return denied;
  try {
    return Response.json({ sessions: await activeSessions() });
  } catch (error) {
    return failure(error);
  }
}
