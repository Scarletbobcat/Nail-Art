type HttpErrorLike = {
  response?: {
    status?: number;
  };
};

export function getHttpStatus(error: unknown) {
  if (!error || typeof error !== "object") {
    return undefined;
  }

  return (error as HttpErrorLike).response?.status;
}
