import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Session } from '../../core/session';

@Component({ selector: 'app-auth', standalone: true, imports: [FormsModule], templateUrl: './auth.html', styleUrl: './auth.css' })
export class Auth {
  private readonly api = inject(CreditApi);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);

  mode: 'login' | 'register' = 'login';
  loading = false;
  error = '';
  login = { username: '', password: '' };
  register = { username: '', password: '', firstName: '', lastName: '', phoneNumber: '', address: '' };

  switchMode(mode: 'login' | 'register') {
    this.mode = mode;
    this.error = '';
  }

  async submit() {
    if (this.loading) return;
    this.error = '';
    this.loading = true;

    try {
      const data = this.mode === 'login' ? this.login : this.register;
      if (this.mode === 'register' && !/^\d{10}$/.test(String(this.register.phoneNumber))) {
        throw new Error('Phone number must contain exactly 10 digits.');
      }

      const response = await this.api.request<any>('auth', `/api/auth/${this.mode}`, {
        method: 'POST',
        body: JSON.stringify({
          ...data,
          phoneNumber: this.mode === 'register' ? Number(this.register.phoneNumber) : undefined,
        }),
      });
      this.session.save(response);
      await this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl') || '/');
    } catch (error: any) {
      this.error = error?.message || 'Unable to sign in.';
    } finally {
      this.loading = false;
      // Fetch completion can happen outside Angular's normal form event cycle.
      // Force the concise API error and button state to be rendered immediately.
      this.changeDetector.detectChanges();
    }
  }
}