import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { UserService } from '../services/loginhandler.service';
import { catchError, throwError } from 'rxjs';

export const loginInterceptor: HttpInterceptorFn = (req, next) => {
  const userService = inject(UserService);

  // Decentralized Embedded BFF Architecture: Browser NEVER attaches Bearer tokens in JS.
  // The opaque HttpOnly cookie (__Host-OmniSession) is sent automatically with withCredentials: true.
  // Each target microservice's BffSessionAuthenticationFilter resolves the session directly from Valkey.
  // X-Requested-With header provides CSRF defense-in-depth (browsers never auto-send custom headers cross-origin).
  const authReq = req.clone({
    withCredentials: true,
    setHeaders: {
      'X-Requested-With': 'XMLHttpRequest'
    }
  });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if ((error.status === 401 || error.status === 403) && !req.url.includes('/auth/')) {
        console.warn('Session expired or unauthorized - redirecting to BFF Login');
        userService.loginWithKeycloak();
      }
      return throwError(() => error);
    })
  );
};