import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";

import api from "../api/api";
import { fetchMe, type MeResponse } from "../api/me";
import { RequireMe } from "../components/RequireMe";
import { useMe } from "./useMe";
import * as useMeModule from "./useMe";

vi.mock("../api/me", () => ({
  fetchMe: vi.fn(),
}));

const meResponse: MeResponse = {
  user: {
    id: "11111111-1111-4111-8111-111111111111",
    username: "owner",
    role: "owner",
  },
  organization: {
    id: "22222222-2222-4222-8222-222222222222",
    name: "Salon 1",
    timezone: "America/New_York",
    businessPhone: "555-0100",
  },
};

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        gcTime: 0,
      },
    },
  });
}

function queryWrapper(client = createQueryClient()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function mockedFetchMe() {
  return vi.mocked(fetchMe);
}

function axiosError(status: number) {
  return {
    response: {
      status,
      data: {
        detail: status === 401 ? "Unauthorized" : "Service unavailable",
      },
    },
    config: {},
  };
}

function responseInterceptor() {
  return (
    api.interceptors.response as unknown as {
      handlers: Array<{ rejected: (error: unknown) => Promise<unknown> }>;
    }
  ).handlers[0];
}

describe("useMe", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    mockedFetchMe().mockReset();
    localStorage.clear();
  });

  it("returns data after fetching /users/me", async () => {
    mockedFetchMe().mockResolvedValueOnce(meResponse);

    const { result } = renderHook(() => useMe(), {
      wrapper: queryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedFetchMe()).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual(meResponse);
  });

  it("lets the axios interceptor clear auth state on 401", async () => {
    localStorage.setItem("token", "stale-token");
    window.history.pushState({}, "", "/Appointments");

    await expect(
      responseInterceptor().rejected({
        response: {
          status: 401,
          data: "Invalid or expired refresh token",
        },
        config: { _retry: true },
      })
    ).rejects.toBeDefined();

    expect(localStorage.getItem("token")).toBeNull();
    expect(localStorage.getItem("previousUrl")).toBe("/Appointments");
  });

  it("does not trigger the logout interceptor on 503", async () => {
    localStorage.setItem("token", "still-valid");
    window.history.pushState({}, "", "/Appointments");

    const unavailable = {
      response: {
        status: 503,
        data: { detail: "Service unavailable" },
      },
      config: {},
    };

    await expect(responseInterceptor().rejected(unavailable)).rejects.toBe(unavailable);

    expect(localStorage.getItem("token")).toBe("still-valid");
    expect(localStorage.getItem("previousUrl")).toBeNull();
  });

  it("surfaces 503 as an error after the configured retries", async () => {
    vi.useFakeTimers();
    mockedFetchMe().mockRejectedValue(axiosError(503));

    const { result } = renderHook(() => useMe(), {
      wrapper: queryWrapper(),
    });

    await waitFor(() => expect(mockedFetchMe()).toHaveBeenCalledTimes(1));
    await act(async () => {
      await vi.runAllTimersAsync();
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
    expect(mockedFetchMe()).toHaveBeenCalledTimes(4);
  });
});

describe("RequireMe", () => {
  let useMeSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    useMeSpy = vi.spyOn(useMeModule, "useMe");
  });

  function mockUseMe(state: Record<string, unknown>) {
    useMeSpy.mockReturnValue({
      data: undefined,
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      isPending: false,
      refetch: vi.fn(),
      ...state,
    } as ReturnType<typeof useMe>);
  }

  function renderProtected() {
    return render(
      <MemoryRouter initialEntries={["/Appointments"]}>
        <Routes>
          <Route
            path="/Appointments"
            element={
              <RequireMe>
                <div>Protected content</div>
              </RequireMe>
            }
          />
          <Route path="/Login" element={<div>Login screen</div>} />
        </Routes>
      </MemoryRouter>
    );
  }

  it("renders CircularLoading while /users/me is loading", () => {
    mockUseMe({ isLoading: true, isPending: true });

    renderProtected();

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });

  it("redirects 401 responses to Login", async () => {
    mockUseMe({ isError: true, error: axiosError(401) });

    renderProtected();

    expect(await screen.findByText("Login screen")).toBeInTheDocument();
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });

  it("renders a retry fallback after three failed non-401 retries with no cache", () => {
    const refetch = vi.fn();
    mockUseMe({
      isError: true,
      error: axiosError(503),
      failureCount: 4,
      refetch,
    });

    renderProtected();

    expect(screen.getByText("Unable to reach the server. Tap to retry.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /retry/i }));
    expect(refetch).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });

  it("renders children only once MeResponse data is defined", () => {
    mockUseMe({ data: meResponse, isSuccess: true });

    renderProtected();

    expect(screen.getByText("Protected content")).toBeInTheDocument();
  });
});
