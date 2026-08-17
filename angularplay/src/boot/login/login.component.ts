import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from '../../services/loginhandler.service';

/**
 * Clean SSO Login Redirection Component.
 * Automatically delegates authentication initiation to Keycloak OIDC with PKCE.
 */
@Component({
  selector: 'app-login',
  template: `
    <div style="display: flex; justify-content: center; align-items: center; min-height: 80vh; color: #94a3b8; font-family: sans-serif;">
      <div style="text-align: center;">
        <div style="font-size: 36px; margin-bottom: 12px; animation: spin 1s linear infinite;">🔄</div>
        <p>Redirecting to OmniBus Secure Authentication...</p>
      </div>
    </div>
  `,
  styles: [`
    @keyframes spin { 100% { transform: rotate(360deg); } }
  `],
  standalone: false
})
export class LoginComponent implements OnInit {
  private userService = inject(UserService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const redirect = params['redirect'] || '';
      this.userService.loginWithKeycloak(redirect);
    });
  }
}
