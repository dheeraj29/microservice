import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Modern Zero-Trust Logout & Audit Confirmation Page.
 */
@Component({
  selector: 'app-logout',
  template: `
    <div class="logout-page-wrapper">
      <div class="logout-card">
        <div class="logout-icon-bubble">
          <span class="icon">🔒</span>
        </div>
        <h1 class="logout-title">You Have Safely Signed Out</h1>
        <p class="logout-subtitle">
          Your distributed session and authentication tokens have been securely purged from the cluster.
        </p>

        <div class="security-badges-container">
          <div class="security-badge">
            <span class="dot"></span>
            <span>HttpOnly Session Cleared</span>
          </div>
          <div class="security-badge">
            <span class="dot"></span>
            <span>Valkey Mutex Released</span>
          </div>
          <div class="security-badge">
            <span class="dot"></span>
            <span>0 Client Tokens Retained</span>
          </div>
        </div>

        <div class="action-buttons-group">
          <button class="btn-primary" (click)="goHome()">
            Return to Home Page 🏠
          </button>
        </div>

        <div class="logout-footer">
          OmniBus Cloud-Native Enterprise Platform • Zero-Trust BFF Security
        </div>
      </div>
    </div>
  `,
  styles: [`
    .logout-page-wrapper {
      min-height: 85vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
      background: radial-gradient(circle at top center, #1e293b 0%, #0f172a 100%);
    }

    .logout-card {
      width: 100%;
      max-width: 480px;
      background: rgba(30, 41, 59, 0.75);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      padding: 40px 32px;
      text-align: center;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
      animation: slideUp 0.35s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes slideUp {
      from { opacity: 0; transform: translateY(16px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .logout-icon-bubble {
      width: 68px;
      height: 68px;
      margin: 0 auto 20px;
      background: rgba(37, 99, 235, 0.15);
      border: 1px solid rgba(37, 99, 235, 0.35);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .logout-icon-bubble .icon {
      font-size: 32px;
    }

    .logout-title {
      font-size: 24px;
      font-weight: 700;
      color: #f8fafc;
      letter-spacing: -0.5px;
      margin-bottom: 8px;
    }

    .logout-subtitle {
      font-size: 14px;
      color: #94a3b8;
      line-height: 1.6;
      margin-bottom: 24px;
    }

    .security-badges-container {
      display: flex;
      flex-direction: column;
      gap: 8px;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 10px;
      padding: 14px 16px;
      margin-bottom: 28px;
      text-align: left;
    }

    .security-badge {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      color: #cbd5e1;
    }

    .security-badge .dot {
      width: 8px;
      height: 8px;
      background: #10b981;
      border-radius: 50%;
      box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
    }

    .action-buttons-group {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .btn-primary {
      width: 100%;
      padding: 13px;
      background: #2563eb;
      color: #ffffff;
      border: none;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.2s, transform 0.1s;
    }

    .btn-primary:hover {
      background: #1d4ed8;
    }

    .btn-primary:active {
      transform: scale(0.99);
    }

    .btn-secondary {
      width: 100%;
      padding: 12px;
      background: rgba(255, 255, 255, 0.05);
      color: #94a3b8;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-secondary:hover {
      background: rgba(255, 255, 255, 0.1);
      color: #f8fafc;
    }

    .logout-footer {
      margin-top: 24px;
      font-size: 12px;
      color: #64748b;
    }
  `],
  standalone: false
})
export class LogoutComponent {
  private router = inject(Router);

  goHome() {
    this.router.navigate(['/']);
  }
}
