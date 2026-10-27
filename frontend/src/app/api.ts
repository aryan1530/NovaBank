import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  login(body: { username: string; password: string }) { return this.http.post<any>('/api/v1/auth/login', body); }
  register(body: any) { return this.http.post<any>('/api/v1/auth/register', body); }
  me() { return this.http.get<any>('/api/v1/auth/me'); }
  profile() { return this.http.get<any>('/api/v1/customers/me'); }
  updateProfile(body: any) { return this.http.put<any>('/api/v1/customers/me', body); }
  accounts() { return this.http.get<any[]>('/api/v1/accounts'); }
  openAccount(type: string) { return this.http.post<any>('/api/v1/accounts', { type }); }
  transfer(key: string, body: any) { return this.http.post<any>('/api/v1/transfers', body, { headers: { 'Idempotency-Key': key } }); }
  history(accountId?: number) { return this.http.get<any[]>('/api/v1/transfers', { params: accountId ? { accountId } : {} }); }
  statement(id: number) { return this.http.get<any>('/api/v1/statements/' + id); }
  notifications() { return this.http.get<any[]>('/api/v1/notifications'); }
  alerts() { return this.http.get<any[]>('/api/v1/fraud/alerts'); }
  fraudMetrics() { return this.http.get<any>('/api/v1/fraud/model-metrics'); }
  demoOverview() { return this.http.get<any>('/api/v1/fraud/demo-overview'); }
  reviewAlert(id: number, status: string) { return this.http.post<any>(`/api/v1/fraud/alerts/${id}/review`, { status }); }
  customers() { return this.http.get<any[]>('/api/v1/customers'); }
  fd(body: any) { return this.http.post('/api/v1/fixed-deposits', body); }
  loan(body: any) { return this.http.post('/api/v1/loans', body); }
  loans() { return this.http.get<any[]>('/api/v1/loans'); }
  schedule(body: any) { return this.http.post('/api/v1/scheduled-payments', body); }
  assistantChat(message: string) { return this.http.post<{message:string;intent:string;dataSource:string;safeMode:boolean;answeredAt:string}>('/api/v1/assistant/chat', { message }); }
}
