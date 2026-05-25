import { useQuery } from "@tanstack/react-query";

import { fetchMe } from "../api/me";
import { getHttpStatus } from "../utils/httpError";

export const meQueryKey = ["me"] as const;

export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: fetchMe,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: (failureCount, error) => getHttpStatus(error) !== 401 && failureCount < 3,
  });
}
