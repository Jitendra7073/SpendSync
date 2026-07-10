import { NextResponse } from 'next/server';

/**
 * Standard API response envelope — same shape the Express version sent, so
 * the Android app's DTO parsing needs no changes.
 */
export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: {
    message: string;
    code?: string;
    details?: unknown;
  };
  meta?: {
    page?: number;
    limit?: number;
    total?: number;
    totalPages?: number;
  };
}

export function success<T>(
  data: T,
  statusCode: number = 200,
  meta?: ApiResponse['meta']
): NextResponse<ApiResponse<T>> {
  const body: ApiResponse<T> = { success: true, data };
  if (meta) body.meta = meta;
  return NextResponse.json(body, { status: statusCode });
}

export function created<T>(data: T): NextResponse<ApiResponse<T>> {
  return success(data, 201);
}

export function noContent(): NextResponse {
  return new NextResponse(null, { status: 204 });
}

export function errorResponse(
  message: string,
  statusCode: number = 500,
  code?: string,
  details?: unknown
): NextResponse<ApiResponse> {
  return NextResponse.json(
    { success: false, error: { message, code, details } },
    { status: statusCode }
  );
}
