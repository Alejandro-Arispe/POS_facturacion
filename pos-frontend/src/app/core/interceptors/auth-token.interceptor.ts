import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();
  const headers: Record<string, string> = {
    'X-Client-Time': new Date().toISOString(),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return next(request.clone({ setHeaders: headers }));
};
