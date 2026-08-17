import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../services/loginhandler.service';

@Component({
  selector: 'app-callback',
  template: `
    <div class="d-flex flex-column align-items-center justify-content-center" style="min-height: 80vh;">
      <div class="glass-card text-center p-5" style="max-width: 500px; width: 90%;">
        @if (errorMessage()) {
          <div class="text-danger mb-3" style="font-size: 3rem;">
            <i class="bi bi-exclamation-octagon-fill"></i>
          </div>
          <h3 class="text-danger">Authentication Failed</h3>
          <p class="text-muted mt-2">{{ errorMessage() }}</p>
          <button class="btn-modern btn-primary mt-4" (click)="retryLogin()">
            <i class="bi bi-arrow-repeat"></i> Try Again
          </button>
        } @else {
          <div class="spin-icon text-primary mb-3" style="font-size: 3.5rem;">
            <i class="bi bi-arrow-clockwise"></i>
          </div>
          <h3>Establishing Secure Session</h3>
          <p class="text-muted mt-2">Exchanging OIDC authorization code and initializing Valkey session...</p>
        }
      </div>
    </div>
  `,
  standalone: false
})
export class CallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private userService = inject(UserService);

  errorMessage = signal<string | null>(null);

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      const code = params.get('code');
      const state = params.get('state');
      const error = params.get('error') || params.get('error_description');

      if (error) {
        this.errorMessage.set(`Keycloak Error: ${error}`);
        return;
      }

      if (!code) {
        this.errorMessage.set('No authorization code received from Keycloak.');
        return;
      }

      // Exchange code via BFF Gateway with PKCE state verification
      let callbackUrl = `/auth/callback?code=${encodeURIComponent(code)}`;
      if (state) {
        callbackUrl += `&state=${encodeURIComponent(state)}`;
      }
      this.http.get<any>(callbackUrl, { withCredentials: true }).subscribe({
        next: (res) => {
          if (res && res.authenticated) {
            this.userService.currentUser.set({
              username: res.username,
              fullName: res.username,
              email: '',
              roles: res.roles || []
            });

            if (res.targetUrl && res.targetUrl !== '/logout' && res.targetUrl !== '/login' && res.targetUrl !== '/callback') {
              this.router.navigateByUrl(res.targetUrl);
            } else if (res.isAdmin) {
              this.router.navigate(['/admin']);
            } else {
              this.router.navigate(['/booking']);
            }
          } else {
            this.errorMessage.set('Unable to validate authentication session with Gateway.');
          }
        },
        error: (err) => {
          this.errorMessage.set(err.error?.error || 'Authentication code exchange failed. Please try logging in again.');
        }
      });
    });
  }

  retryLogin() {
    this.userService.loginWithKeycloak();
  }
}
