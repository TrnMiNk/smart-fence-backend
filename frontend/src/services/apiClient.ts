const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...init?.headers },
    });
  } catch {
    throw new ApiError('Không thể kết nối Backend. Hãy kiểm tra Backend và VITE_API_BASE_URL.');
  }

  const body = await response.text();
  if (!response.ok) {
    let message = `Backend trả về HTTP ${response.status}.`;
    if (body) {
      try {
        const errorBody: unknown = JSON.parse(body);
        if (typeof errorBody === 'object' && errorBody !== null && 'message' in errorBody && typeof errorBody.message === 'string') {
          message = errorBody.message;
        }
      } catch {
        // Giữ thông báo HTTP khi body lỗi không phải JSON hợp lệ.
      }
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204 || !body.trim()) return undefined as T;
  try {
    return JSON.parse(body) as T;
  } catch {
    throw new ApiError('Backend trả về JSON không hợp lệ.', response.status);
  }
}
