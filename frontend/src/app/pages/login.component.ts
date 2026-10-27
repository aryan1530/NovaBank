import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from '../api';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `<div class="login-page"><section class="login-visual"><a class="login-brand" href="/"><span class="brand-mark">N</span><span>nova<span style="font-weight:500;color:#b7c5dc">bank</span></span></a><div class="login-visual-copy"><div class="eyebrow">BANKING THAT MOVES WITH YOU</div><h2>A clearer view<br>of your money.</h2><p>Simple, thoughtful banking to help you manage today and plan for what comes next.</p><div class="login-feature"><span>✓</span> Secure access, protected by design</div><div class="login-feature"><span>↗</span> Your accounts, all in one place</div></div><div class="login-foot">© 2026 NovaBank · A simulated banking experience</div></section><section class="login-side"><div class="login-card"><div class="eyebrow">WELCOME TO NOVABANK</div><h1>{{mode==='login'?'Welcome back':'Create your account'}}</h1><p>{{mode==='login'?'Sign in to continue to your banking dashboard.':'Get started with secure digital banking.'}}</p><label>Username</label><input [(ngModel)]="username" autocomplete="username" placeholder="Enter your username" (keyup.enter)="submit()"><label>Password</label><input type="password" [(ngModel)]="password" autocomplete="current-password" placeholder="Enter your password" (keyup.enter)="submit()"><div *ngIf="mode==='register'"><label>Email address</label><input type="email" [(ngModel)]="email" autocomplete="email" placeholder="you@example.com"><label>Full name</label><input [(ngModel)]="fullName" autocomplete="name" placeholder="Your full name"></div><div class="login-error" *ngIf="error">{{error}}</div><button class="primary-button login-submit" (click)="submit()" [disabled]="busy">{{busy?'Please wait…':mode==='login'?'Sign in securely →':'Create account →'}}</button><button class="login-switch" (click)="mode=mode==='login'?'register':'login';error=''">{{mode==='login'?"New to NovaBank? Create an account":"Already have an account? Sign in"}}</button><div class="login-note"><strong>Demo environment</strong><br>Transactions and payment channels are simulated. Do not use real credentials.</div><div class="login-demo" *ngIf="mode==='login'">Demo access: admin / AdminPassw0rd! · analyst / AnalystPassw0rd!</div></div></section></div>`
})
export class LoginComponent {
  private api = inject(Api); private router = inject(Router);
  mode: 'login' | 'register' = 'login'; username = 'admin'; password = 'AdminPassw0rd!'; email = ''; fullName = ''; error = ''; busy = false;
  submit() {
    this.error = '';
    if (!this.username.trim() || !this.password) { this.error = 'Enter your username and password to continue.'; return; }
    if (this.mode === 'register' && (!this.email.trim() || !this.fullName.trim())) { this.error = 'Enter your email and full name to create an account.'; return; }
    this.busy = true;
    const req = this.mode === 'login' ? this.api.login({ username: this.username.trim(), password: this.password }) : this.api.register({ username: this.username.trim(), password: this.password, email: this.email.trim(), fullName: this.fullName.trim() });
    req.subscribe({ next: t => { this.busy = false; localStorage.setItem('accessToken', t.accessToken); localStorage.setItem('refreshToken', t.refreshToken); localStorage.setItem('roles', JSON.stringify(t.roles || [])); this.router.navigateByUrl('/'); }, error: e => { this.busy = false; this.error = e.error?.message || 'We could not sign you in. Check your details and try again.'; } });
  }
}
