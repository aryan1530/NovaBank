import { HttpInterceptorFn } from '@angular/common/http';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, finalize, map, shareReplay, switchMap, throwError } from 'rxjs';

let refreshInFlight: Observable<string> | null = null;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const backend = inject(HttpBackend);
  const token = localStorage.getItem('accessToken');
  const authorized = token ? req.clone({ setHeaders: { Authorization: 'Bearer ' + token } }) : req;
  return next(authorized).pipe(catchError(error => {
    if (error.status !== 401 || req.url.includes('/api/v1/auth/')) return throwError(() => error);
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return expireSession(error);

    if (!refreshInFlight) {
      const rawHttp = new HttpClient(backend);
      refreshInFlight = rawHttp.post<{ accessToken: string; refreshToken: string; roles?: string[] }>(
        '/api/v1/auth/refresh', { refreshToken }
      ).pipe(
        map(tokens => {
          localStorage.setItem('accessToken', tokens.accessToken);
          localStorage.setItem('refreshToken', tokens.refreshToken);
          localStorage.setItem('roles', JSON.stringify(tokens.roles || []));
          return tokens.accessToken as string;
        }),
        finalize(() => refreshInFlight = null),
        shareReplay(1)
      );
    }
    return refreshInFlight.pipe(
      switchMap(accessToken => next(req.clone({ setHeaders: { Authorization: 'Bearer ' + accessToken } }))),
      catchError(refreshError => expireSession(refreshError))
    );
  }));
};

function expireSession(error: unknown) {
  localStorage.clear();
  window.location.assign('/login');
  return throwError(() => error);
}
