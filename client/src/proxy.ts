import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const AUTH_COOKIE = "springlens_auth";

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isAuthed = request.cookies.get(AUTH_COOKIE)?.value === "1";

  if (pathname.startsWith("/auth/callback")) {
    return NextResponse.next();
  }

  if (pathname.startsWith("/dashboard") || pathname.startsWith("/chat")) {
    if (!isAuthed) {
      const loginUrl = request.nextUrl.clone();
      loginUrl.pathname = "/login";
      loginUrl.search = "";
      loginUrl.searchParams.set("next", pathname);
      return NextResponse.redirect(loginUrl);
    }
  }

  if (pathname === "/login" && isAuthed) {
    const dash = request.nextUrl.clone();
    dash.pathname = "/dashboard";
    dash.search = "";
    return NextResponse.redirect(dash);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/chat/:path*", "/login", "/auth/callback"],
};
