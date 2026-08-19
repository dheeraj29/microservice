import { Injectable, inject, signal, computed } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';

export interface UserProfile {
  username: string;
  fullName: string;
  email: string;
  roles: string[];
  language?: string;
  timezone?: string;
  homepage?: string;
  theme?: string;
}

export interface BffUserResponse {
  authenticated: boolean;
  username: string;
  roles: string[];
  isAdmin: boolean;
  language?: string;
  timezone?: string;
  homepage?: string;
  theme?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private router = inject(Router);

  // Relative path ensures same-origin cookie transmission via proxy
  readonly bffAuthUrl = '/auth';

  // Current User Signal
  currentUser = signal<UserProfile | null>(null);
  isInitializing = signal<boolean>(true);

  isAuthenticated = computed(() => this.currentUser() !== null);
  isAdmin = computed(() => this.hasRole('ADMIN') || this.hasRole('ROLE_ADMIN'));
  isUser = computed(() => this.hasRole('USER') || this.hasRole('ROLE_USER'));

  constructor() {
    this.restoreSession().subscribe();
  }

  /**
   * Initiates OIDC Federated SSO Login redirect via Keycloak (prompt=login forced).
   */
  loginWithKeycloak(redirectPath?: string) {
    const candidate = redirectPath || (window.location.pathname + window.location.search);
    const pathOnly = candidate ? candidate.split('?')[0] : '';
    const isSpecialPath = !candidate ||
                          pathOnly === '' ||
                          pathOnly === '/' ||
                          pathOnly === '/callback' ||
                          pathOnly === '/login' ||
                          pathOnly === '/logout';

    const target = isSpecialPath ? '' : candidate;
    if (target) {
      window.location.href = `${this.bffAuthUrl}/login?redirect=${encodeURIComponent(target)}`;
    } else {
      window.location.href = `${this.bffAuthUrl}/login`;
    }
  }

  /**
   * Restores authenticated session via BFF Gateway HttpOnly Cookie.
   */
  restoreSession(): Observable<boolean> {
    return this.http.get<BffUserResponse>(`${this.bffAuthUrl}/user`, { withCredentials: true }).pipe(
      tap((res) => {
        this.isInitializing.set(false);
        if (res && res.authenticated) {
          this.currentUser.set({
            username: res.username,
            fullName: res.username,
            email: '',
            roles: res.roles || [],
            language: res.language || 'en',
            timezone: res.timezone || 'Asia/Kolkata',
            homepage: res.homepage || '/booking',
            theme: res.theme || 'dark'
          });
        } else {
          this.currentUser.set(null);
        }
      }),
      map((res) => !!(res && res.authenticated)),
      catchError(() => {
        this.isInitializing.set(false);
        this.currentUser.set(null);
        return of(false);
      })
    );
  }

  /**
   * Update user preferences (language, timezone, homepage, theme) across cluster session.
   */
  updatePreferences(prefs: { language?: string; timezone?: string; homepage?: string; theme?: string }): Observable<UserProfile | null> {
    return this.http.put<BffUserResponse>(`${this.bffAuthUrl}/user/preferences`, prefs, { withCredentials: true }).pipe(
      map(res => {
        if (res && res.authenticated) {
          const profile: UserProfile = {
            username: res.username,
            fullName: res.username,
            email: '',
            roles: res.roles || [],
            language: res.language || 'en',
            timezone: res.timezone || 'Asia/Kolkata',
            homepage: res.homepage || '/booking',
            theme: res.theme || 'dark'
          };
          this.currentUser.set(profile);
          return profile;
        }
        return null;
      }),
      catchError(() => of(null))
    );
  }

  hasRole(role: string): boolean {
    const user = this.currentUser();
    if (!user || !user.roles) return false;
    const normalized = role.toUpperCase();
    return user.roles.some(r => r.toUpperCase() === normalized || r.toUpperCase() === `ROLE_${normalized}`);
  }

  /**
   * Terminate session via API and route to the Signed Out Confirmation page.
   */
  logout() {
    this.http.post(`${this.bffAuthUrl}/logout`, {}, { withCredentials: true }).subscribe({
      next: () => {
        this.currentUser.set(null);
        this.router.navigate(['/logout']);
      },
      error: () => {
        this.currentUser.set(null);
        this.router.navigate(['/logout']);
      }
    });
  }
}

export const AuthGuard: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> | boolean => {
  const userService = inject(UserService);

  if (userService.isAuthenticated()) {
    return true;
  }

  return userService.restoreSession().pipe(
    map(isAuth => {
      if (isAuth) {
        return true;
      }
      userService.loginWithKeycloak(state.url);
      return false;
    })
  );
};

export const AdminGuard: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> | boolean => {
  const userService = inject(UserService);
  const router = inject(Router);

  if (userService.isAuthenticated()) {
    if (userService.isAdmin()) return true;
    router.navigate(['/booking']);
    return false;
  }

  return userService.restoreSession().pipe(
    map(isAuth => {
      if (isAuth && userService.isAdmin()) {
        return true;
      }
      if (isAuth) {
        router.navigate(['/booking']);
        return false;
      }
      userService.loginWithKeycloak(state.url);
      return false;
    })
  );
};