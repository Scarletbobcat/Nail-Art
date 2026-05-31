import { render, screen } from "@testing-library/react";

import { RequireMe } from "./RequireMe";
import { useMe } from "../hooks/useMe";
import type { MeResponse } from "../api/me/me";

vi.mock("../hooks/useMe", () => ({ useMe: vi.fn() }));
vi.mock("react-router-dom", () => ({
  Navigate: ({ to }: { to: string }) => <div data-testid="navigate">{to}</div>,
}));

const admin: MeResponse = {
  user: { id: "a", username: "operator", role: null, isPlatformAdmin: true },
  organization: null,
};
const owner: MeResponse = {
  user: { id: "o", username: "owner", role: "owner", isPlatformAdmin: false },
  organization: { id: "org", name: "Salon", timezone: "America/New_York", businessPhone: "555" },
};

function asLoaded(data: MeResponse) {
  vi.mocked(useMe).mockReturnValue({
    data,
    error: null,
    isError: false,
    isLoading: false,
    isPending: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useMe>);
}

function child() {
  return <div data-testid="child">protected</div>;
}

afterEach(() => vi.clearAllMocks());

describe("RequireMe platform-admin gating", () => {
  it("renders an admin-only route for a platform admin", () => {
    asLoaded(admin);
    render(<RequireMe requirePlatformAdmin>{child()}</RequireMe>);
    expect(screen.getByTestId("child")).toBeInTheDocument();
  });

  it("redirects a non-admin away from an admin-only route", () => {
    asLoaded(owner);
    render(<RequireMe requirePlatformAdmin>{child()}</RequireMe>);
    expect(screen.getByTestId("navigate")).toHaveTextContent("/appointments");
    expect(screen.queryByTestId("child")).not.toBeInTheDocument();
  });

  it("redirects a platform admin off a salon route to the console", () => {
    asLoaded(admin);
    render(<RequireMe>{child()}</RequireMe>);
    expect(screen.getByTestId("navigate")).toHaveTextContent("/admin");
  });

  it("renders a salon route for an owner", () => {
    asLoaded(owner);
    render(<RequireMe>{child()}</RequireMe>);
    expect(screen.getByTestId("child")).toBeInTheDocument();
  });

  it("still enforces requiredRole for non-admin users", () => {
    asLoaded({ ...owner, user: { ...owner.user, role: "staff" } });
    render(
      <RequireMe requiredRole="owner">
        {child()}
      </RequireMe>
    );
    expect(screen.getByTestId("navigate")).toHaveTextContent("/appointments");
  });
});
