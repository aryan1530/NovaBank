import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from '../api';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="app-frame theme-night">
    <aside class="sidebar" [class.mobile-open]="menuOpen">
      <a class="brand" href="/"><span class="brand-mark">N</span><span>nova<span class="brand-light">bank</span><small>PERSONAL BANKING</small></span></a>
      <div class="nav-caption">WORKSPACE</div>
      <button class="nav-link" [class.active]="tab==='home'" (click)="go('home')"><span>◫</span> Overview</button>
      <button class="nav-link" [class.active]="tab==='transfer'" (click)="go('transfer')"><span>⇄</span> Transfers</button>
      <button class="nav-link" [class.active]="tab==='history'" (click)="go('history')"><span>▤</span> Activity</button>
      <button class="nav-link" [class.active]="tab==='products'" (click)="go('products')"><span>◈</span> Products</button>
      <button class="nav-link ai-nav-link" [class.active]="tab==='assistant'" (click)="go('assistant')"><span>✦</span> Nova AI</button>
      <div class="nav-caption staff-caption" *ngIf="isStaff">OPERATIONS</div>
      <button class="nav-link" *ngIf="isStaff" [class.active]="tab==='alerts'" (click)="go('alerts')"><span>⌁</span> Risk monitor <i *ngIf="alerts.length">{{alerts.length}}</i></button>
      <button class="nav-link" *ngIf="isAdmin" [class.active]="tab==='admin'" (click)="go('admin')"><span>⌘</span> Customers</button>
      <div class="sidebar-bottom"><div class="help-card"><span class="help-icon">✦</span><strong>Here for you</strong><p>Your money, made simple. Need help? Our team is one click away.</p><a href="mailto:support@novabank.local">Contact support ↗</a></div><div class="profile-mini"><div class="avatar">{{initials}}</div><div class="profile-copy"><strong>{{username || 'Your profile'}}</strong><small>{{isAdmin ? 'Administrator' : 'Personal account'}}</small></div><button class="icon-button" title="Sign out" (click)="logout()">↗</button></div></div>
    </aside>
    <main class="main-area">
      <header class="topbar"><button class="mobile-menu icon-button" (click)="menuOpen=!menuOpen">☰</button><div class="crumb">My banking <span>/</span> <strong>{{pageTitle}}</strong></div><div class="top-actions"><span class="secure-pill"><span></span> Secure session</span><span class="live-pill"><i></i> Live · {{lastUpdated | date:"h:mm:ss a"}}</span><button class="notification-button" (click)="go('home')" title="Notifications">♧<b *ngIf="notes.length">{{notes.length}}</b></button><div class="avatar avatar-small">{{initials}}</div></div></header>
      <div class="page-content">
        <div class="toast success-toast" *ngIf="successMessage">✓ {{successMessage}}<button (click)="successMessage=''">×</button></div>
        <div class="toast error-toast" *ngIf="error">! {{error}}<button (click)="error=''">×</button></div>
        <section class="hero-banner" *ngIf="tab==='home'"><div class="hero-copy"><span class="hero-kicker">NOVA BANK · YOUR FINANCIAL SPACE</span><h1>{{greeting}}, {{firstName}} <span>✦</span></h1><p>A clear view of your money, activity and financial tools.</p><span class="hero-chip" *ngIf="isAdmin">✦ Synthetic portfolio data · read only</span><span class="hero-chip" *ngIf="!isAdmin">⌑ Private customer workspace</span></div><div class="hero-right"><div><span>{{lastUpdated | date:'EEE, d MMM y'}}</span><strong>{{lastUpdated | date:'h:mm a'}}</strong></div><button class="primary-button" *ngIf="!isAdmin" (click)="go('transfer')"><span>＋</span> New transfer</button></div><div class="hero-orbit orbit-one"></div><div class="hero-orbit orbit-two"></div></section>
        <div class="page-heading compact-heading" *ngIf="tab!=='home'"><div><div class="eyebrow">NOVA BANKING</div><h1>{{pageTitle}}</h1><p>{{pageDescription}}</p></div><button *ngIf="tab==='history'" class="outline-button" (click)="loadHistory()">↻ Refresh</button></div>
        <div class="dashboard-layout" *ngIf="tab==='home'"><div class="dashboard-core">
        <div class="stat-grid" *ngIf="tab==='home'">
          <div class="stat-card balance-card"><div class="stat-top"><span>{{isAdmin ? 'Sample portfolio balance' : 'Total balance'}}</span><span class="stat-symbol">◉</span></div><div class="balance-value">{{(isAdmin && datasetOverview ? datasetOverview.sampleBalanceInr : totalBalance) | currency:'INR':'symbol':'1.2-2'}}</div><div class="stat-foot"><span class="balance-dot"></span> {{isAdmin ? 'Synthetic CSV · read only' : 'Across ' + accounts.length + ' linked accounts'}} <span class="balance-note">INR</span></div><div class="balance-decoration"></div></div>
          <div class="stat-card"><div class="stat-top"><span>{{isAdmin ? 'Sample accounts' : 'Accounts'}}</span><span class="stat-icon mint">▤</span></div><div class="stat-number">{{(isAdmin && datasetOverview ? datasetOverview.activeAccounts : accounts.length) | number:'2.0'}}</div><div class="stat-foot">{{isAdmin ? 'Active synthetic records' : 'Active banking accounts'}}</div></div>
          <div class="stat-card"><div class="stat-top"><span>{{isAdmin ? 'Transactions analyzed' : 'Recent activity'}}</span><span class="stat-icon lilac">↗</span></div><div class="stat-number">{{(isAdmin && datasetOverview ? datasetOverview.transactions : txns.length) | number:'2.0'}}</div><div class="stat-foot">{{isAdmin ? 'Labeled CSV training set' : 'Transactions in your feed'}}</div></div>
        </div>
        <div class="dashboard-grid" *ngIf="tab==='home'">
          <section class="panel accounts-panel"><div class="panel-heading"><div><h2>{{isAdmin ? 'Sample accounts' : 'Your accounts'}}</h2><p>{{isAdmin ? 'Masked records from the uploaded synthetic data' : 'Manage your Nova accounts'}}</p></div><button *ngIf="!isAdmin" class="text-button" (click)="open()" [disabled]="busy">＋ Open account</button><span *ngIf="isAdmin" class="sample-readonly">READ ONLY</span></div>
            <div class="account-list" *ngIf="displayAccounts.length; else noAccounts"><div class="account-row" *ngFor="let a of displayAccounts"><div class="account-symbol" [class.current-account]="a.type==='CURRENT'">{{a.type==='CURRENT'?'▧':'◈'}}</div><div class="account-info"><strong>{{a.type==='SAVINGS'?'Everyday savings':a.type==='CURRENT'?'Current account':a.type}}</strong><span>{{maskAccount(a.accountNumber)}} · {{a.bank || a.currency || 'INR'}}</span></div><div class="account-balance"><strong>{{(a.balance ?? a.balanceInr) | currency:'INR':'symbol':'1.2-2'}}</strong><span class="status-active"><i></i>{{a.status || 'ACTIVE'}}</span></div></div></div>
            <ng-template #noAccounts><div class="empty-state"><div class="empty-icon">◈</div><strong>Your banking starts here</strong><p>Create a customer account to start the simulated banking workflow.</p><button class="primary-button" (click)="registerCustomer()">Create customer profile</button></div></ng-template>
          </section>
          <section class="panel quick-panel"><div class="panel-heading"><div><h2>Quick actions</h2><p>Everyday banking, made easy</p></div></div><div class="quick-actions"><button class="quick-action" (click)="go('transfer')"><span class="action-icon action-blue">⇄</span><strong>Send money</strong><small>Transfer securely</small><span class="action-arrow">↗</span></button><button class="quick-action" (click)="go('history')"><span class="action-icon action-gold">▤</span><strong>View activity</strong><small>Track transactions</small><span class="action-arrow">↗</span></button><button class="quick-action" (click)="go('products')"><span class="action-icon action-purple">◈</span><strong>Explore products</strong><small>Save or borrow</small><span class="action-arrow">↗</span></button></div><div class="security-note"><span>✓</span><div><strong>Your money is protected</strong><small>Every transaction is monitored around the clock.</small></div></div></section>
          <section class="panel activity-panel"><div class="panel-heading"><div><h2>{{isAdmin ? 'Sample activity' : 'Recent activity'}}</h2><p>{{isAdmin ? 'Masked sample from the uploaded CSV · not posted to the ledger' : 'Your latest account transactions'}}</p></div><button *ngIf="!isAdmin" class="text-button" (click)="go('history')">View all ↗</button></div><div class="activity-list" *ngIf="dashboardTransactions.length; else noActivity"><div class="activity-row" *ngFor="let t of dashboardTransactions | slice:0:5"><span class="activity-icon" [class.income-icon]="isIncoming(t)">{{isIncoming(t)?'↙':'↗'}}</span><div class="activity-copy"><strong>{{t.narration || (isIncoming(t)?'Money received':'Money sent')}}</strong><span>{{(t.createdAt || t.date) | date:'MMM d, y · h:mm a'}} · {{t.channel}}</span></div><div class="activity-amount" [class.income-amount]="isIncoming(t)">{{isIncoming(t)?'+':'−'}}{{(t.amount ?? t.amountInr) | currency:'INR':'symbol':'1.2-2'}}<small [class.status-failed]="t.status==='FAILED'">{{t.fraudFlagged ? 'REVIEW FLAG' : (t.status || 'SAMPLE')}}</small></div></div></div><ng-template #noActivity><div class="empty-inline">Your transactions will appear here.</div></ng-template></section>
          <section class="panel analytics-panel"><div class="panel-heading"><div><h2>{{isAdmin ? 'Dataset activity' : 'Spending analytics'}}</h2><p>{{isAdmin ? 'Transaction volume in the supplied CSV · read only' : 'Recorded outgoing transfers · last 6 months'}}</p></div><span class="analytics-range">{{isAdmin ? 'JUL–SEP' : '6 MONTHS'}}</span></div><div class="analytics-total">{{recentSpend | currency:'INR':'symbol':'1.2-2'}}<small>{{isAdmin ? 'synthetic transaction volume' : 'recorded outflow'}}</small></div><div class="bar-chart" *ngIf="analyticsMonths.length"><div class="chart-column" *ngFor="let month of analyticsMonths"><span class="chart-value" *ngIf="month.amount>0">{{month.amount | number:'1.0-0'}}</span><div class="chart-track"><div class="chart-bar" [style.height.%]="barHeight(month.amount)"></div></div><small>{{month.label}}</small></div></div><div class="channel-insight" *ngIf="topChannel"><span>✦</span> Most-used simulated channel: <strong>{{topChannel}}</strong></div><div class="empty-inline" *ngIf="!analyticsMonths.length">No recorded activity to chart yet.</div></section>
          <section class="panel notification-panel"><div class="panel-heading"><div><h2>Inbox</h2><p>Updates for you</p></div><span class="inbox-count">{{notes.length}}</span></div><div class="inbox-list" *ngIf="notes.length; else noNotes"><div class="inbox-item" *ngFor="let n of notes | slice:0:4"><span class="inbox-icon">✦</span><div><strong>{{n.title}}</strong><p>{{n.body}}</p></div></div></div><ng-template #noNotes><div class="empty-inline">You’re all caught up. New updates will show here.</div></ng-template></section>
        </div></div><aside class="dashboard-rail"><section class="panel rail-assistant"><div class="rail-ai-head"><span class="assistant-brand-icon">✦</span><div><strong>Nova AI</strong><small><i></i> Online · private session</small></div></div><p class="rail-greeting">Hi {{firstName}}, I’m Nova. Ask about your balance, activity or how banking features work.</p><div class="rail-suggestions"><button (click)="sendChat('What is my account balance?')">Check account balance <span>→</span></button><button (click)="sendChat('Show my recent activity')">Recent activity <span>→</span></button></div><form class="rail-composer" (ngSubmit)="sendChat()"><input name="railChat" [(ngModel)]="chatInput" maxlength="500" placeholder="Ask Nova anything…" [disabled]="chatBusy"><button [disabled]="chatBusy || !chatInput.trim()" aria-label="Send to Nova">↑</button></form><p class="rail-reply" *ngIf="chatMessages.length>1">{{chatMessages[chatMessages.length-1].text}}</p><button class="rail-open-ai" (click)="go('assistant')">Open Nova AI ↗</button></section><section class="panel rail-updates"><div class="panel-heading"><div><h2>Latest updates</h2><p>Account and security notifications</p></div><span class="inbox-count">{{notes.length}}</span></div><div class="inbox-list" *ngIf="notes.length; else railNoNotes"><div class="inbox-item" *ngFor="let n of notes | slice:0:3"><span class="inbox-icon">✦</span><div><strong>{{n.title}}</strong><p>{{n.body}}</p></div></div></div><ng-template #railNoNotes><div class="empty-inline">New account updates will appear here.</div></ng-template></section><section class="panel rail-risk" *ngIf="isAdmin && datasetOverview"><div class="panel-heading"><div><h2>Fraud intelligence</h2><p>XGBoost · time-separated test data</p></div><span class="risk-live-dot"></span></div><div class="risk-metric"><strong>{{datasetOverview.fraudTransactions | number}}</strong><span>labeled sample fraud cases</span></div><div class="risk-metric"><strong>{{datasetOverview.transactions | number}}</strong><span>transactions analyzed</span></div><div class="risk-meta" *ngIf="fraudMetrics">PR-AUC <strong>{{fraudMetrics.pr_auc | percent:'1.0-1'}}</strong><span>·</span> F1 <strong>{{fraudMetrics.f1 | percent:'1.0-1'}}</strong></div><small class="sample-disclaimer">Synthetic CSV insight only; never represents live customer balances or payment decisions.</small></section></aside></div>
        <section class="panel transfer-panel" *ngIf="tab==='transfer'"><div class="transfer-layout"><div class="transfer-form"><div class="form-intro"><span class="form-icon">⇄</span><div><h2>Send money</h2><p>Transfers are simulated for this portfolio demo.</p></div></div><label>From account</label><select [(ngModel)]="fromId"><option value="">Choose an account</option><option *ngFor="let a of accounts" [value]="a.id">{{a.type}} · {{maskAccount(a.accountNumber)}} · {{a.balance | currency:'INR':'symbol':'1.2-2'}}</option></select><label>Recipient account ID <span class="label-hint">NovaBank demo</span></label><input type="number" min="1" step="1" [(ngModel)]="toId" placeholder="Enter recipient account ID"><div class="form-row"><div><label>Amount</label><div class="amount-input"><span>₹</span><input type="number" min="0.01" step="0.01" [(ngModel)]="amount" placeholder="0.00"></div></div><div><label>Payment method</label><select [(ngModel)]="channel"><option>IMPS</option><option>NEFT</option><option>RTGS</option><option>UPI</option><option>INTERNAL</option></select></div></div><label>Note <span class="label-hint">Optional</span></label><input [(ngModel)]="narration" maxlength="120" placeholder="What is this payment for?"><button class="primary-button submit-transfer" [disabled]="busy || !fromId || !toId || !amount" (click)="send()">{{busy?'Processing…':'Review and send'}} <span>→</span></button><div class="secure-caption">♧ Encrypted and protected · A unique request key helps prevent duplicate transfers</div></div><aside class="transfer-aside"><div class="aside-top"><span class="aside-chip">NOVA PAYMENTS</span><div class="aside-art">⇄</div><h3>Simple, secure<br>transfers.</h3><p>Choose a payment channel that works for you. All channels in this demo are simulated.</p></div><div class="channel-row"><span>⚡</span><div><strong>IMPS / UPI</strong><small>Simulated instant payment</small></div></div><div class="channel-row"><span>◷</span><div><strong>NEFT / RTGS</strong><small>Simulated bank transfer</small></div></div><div class="aside-safety">✓ <span>We’ll show your transfer status as soon as it’s processed.</span></div></aside></div></section>
        <section class="panel assistant-panel" *ngIf="tab==='assistant'">
          <div class="assistant-heading"><div class="assistant-brand-icon">✦</div><div><h2>Nova AI</h2><p>Private banking guide · grounded in your records</p></div><span class="privacy-chip">⌑ PRIVATE SESSION</span></div>
          <div class="assistant-messages" #chatScroll aria-live="polite">
            <div class="chat-row" *ngFor="let message of chatMessages" [class.chat-user-row]="message.role==='user'">
              <span class="chat-avatar" *ngIf="message.role==='assistant'">✦</span><div class="chat-bubble" [class.user-bubble]="message.role==='user'"><span class="chat-label">{{message.role==='assistant'?'NOVA AI':'YOU'}}</span><p>{{message.text}}</p></div>
            </div>
            <div class="chat-row" *ngIf="chatBusy"><span class="chat-avatar">✦</span><div class="chat-bubble typing-bubble"><span class="typing-dots"><i></i><i></i><i></i></span></div></div>
          </div>
          <div class="suggestion-list" *ngIf="chatMessages.length===1 && !chatBusy"><button *ngFor="let suggestion of chatSuggestions" (click)="sendChat(suggestion)">{{suggestion}} <span>→</span></button></div>
          <form class="chat-composer" (ngSubmit)="sendChat()"><input name="chatInput" [(ngModel)]="chatInput" maxlength="500" placeholder="Ask about your accounts or recent activity…" aria-label="Ask Nova AI a question" [disabled]="chatBusy"><button type="submit" [disabled]="chatBusy || !chatInput.trim()" aria-label="Send message">↑</button></form>
          <p class="assistant-disclosure">Nova AI uses controlled intents to answer from your authenticated records and banking guidance. It cannot execute payments; chat is not stored. Simulated demo data only.</p>
        </section>
        <div class="history-layout" *ngIf="tab==='history'"><section class="panel history-panel"><div class="history-toolbar"><div class="search-box"><span>⌕</span><input placeholder="Filter by account ID" [(ngModel)]="filterAccount" (input)="loadHistory()"></div><select class="filter-select" [(ngModel)]="statusFilter"><option value="ALL">All statuses</option><option>COMPLETED</option><option>PENDING_REVIEW</option><option>FAILED</option></select></div><div class="table-wrap"><table><thead><tr><th>TRANSACTION</th><th>DATE & TIME</th><th>CHANNEL</th><th>STATUS</th><th class="align-right">AMOUNT</th></tr></thead><tbody><tr *ngFor="let t of filteredTransactions"><td><div class="table-transaction"><span class="activity-icon" [class.income-icon]="isIncoming(t)">{{isIncoming(t)?'↙':'↗'}}</span><div><strong>{{t.narration || (isIncoming(t)?'Money received':'Money sent')}}</strong><small>Reference {{t.publicId || t.id}}</small></div></div></td><td>{{t.createdAt | date:'MMM d, y · h:mm a'}}</td><td><span class="channel-badge">{{t.channel}}</span></td><td><span class="state-badge" [class.state-pending]="t.status==='PENDING_REVIEW'" [class.state-failed]="t.status==='FAILED'">{{t.status}}</span></td><td class="amount-cell" [class.income-amount]="isIncoming(t)">{{isIncoming(t)?'+':'−'}}{{t.amount | currency:'INR':'symbol':'1.2-2'}}</td></tr></tbody></table><div class="empty-inline" *ngIf="!filteredTransactions.length">No transactions match this filter.</div></div></section><section class="panel statement-panel"><div class="panel-heading"><div><h2>Statement summary</h2><p>Choose an account to view totals</p></div></div><select [(ngModel)]="statementAccount" (change)="loadStatement()"><option value="">Select account</option><option *ngFor="let a of accounts" [value]="a.id">{{a.type}} · {{maskAccount(a.accountNumber)}}</option></select><div class="statement-visual" *ngIf="statement"><div><span>Money in</span><strong class="income-amount">{{statement.inflow | currency:'INR':'symbol':'1.2-2'}}</strong></div><div><span>Money out</span><strong>{{statement.outflow | currency:'INR':'symbol':'1.2-2'}}</strong></div><div class="statement-net"><span>Net activity</span><strong>{{statement.net | currency:'INR':'symbol':'1.2-2'}}</strong></div></div><div class="empty-inline" *ngIf="!statement">Select an account to see your statement summary.</div></section></div>
        <div class="products-grid" *ngIf="tab==='products'"><section class="panel product-panel"><div class="product-banner fd-banner"><span>✳ SAVINGS GOAL</span><div class="product-art">◈</div><h2>Fixed deposit</h2><p>Set money aside and let your savings grow at a fixed rate.</p></div><div class="product-form"><label>Fund from account</label><select [(ngModel)]="fdAccount"><option value="">Choose an account</option><option *ngFor="let a of accounts" [value]="a.id">{{a.type}} · {{maskAccount(a.accountNumber)}}</option></select><div class="form-row"><div><label>Principal (₹)</label><input type="number" min="1000" step="100" [(ngModel)]="fdPrincipal"></div><div><label>Annual rate (%)</label><input type="number" min="0.1" step="0.1" [(ngModel)]="fdRate"></div></div><label>Tenure</label><select [(ngModel)]="fdTenure"><option value="6">6 months</option><option value="12">12 months</option><option value="24">24 months</option><option value="36">36 months</option></select><button class="primary-button full-button" [disabled]="busy || !fdAccount" (click)="openFd()">{{busy?'Submitting…':'Book fixed deposit →'}}</button></div></section><section class="panel product-panel"><div class="product-banner loan-banner"><span>✳ PLAN AHEAD</span><div class="product-art">↗</div><h2>Loan application</h2><p>Tell us what you need and we’ll record your application for review.</p></div><div class="product-form"><label>Linked account</label><select [(ngModel)]="loanAccount"><option value="">Choose an account</option><option *ngFor="let a of accounts" [value]="a.id">{{a.type}} · {{maskAccount(a.accountNumber)}}</option></select><div class="form-row"><div><label>Requested amount (₹)</label><input type="number" min="1000" step="100" [(ngModel)]="loanAmount"></div><div><label>Tenure (months)</label><input type="number" min="1" max="360" [(ngModel)]="loanTenure"></div></div><label>Purpose</label><input [(ngModel)]="loanPurpose" maxlength="120" placeholder="e.g. Home improvement"><button class="outline-button full-button" [disabled]="busy || !loanAccount" (click)="applyLoan()">{{busy?'Submitting…':'Submit application →'}}</button></div></section></div>
        <section class="panel staff-panel" *ngIf="tab==='alerts'"><div class="panel-heading"><div><h2>Fraud risk monitor</h2><p>Model-flagged transfers awaiting review</p></div><span class="inbox-count">{{alerts.length}} alerts</span></div><div class="table-wrap"><table><thead><tr><th>RISK SCORE</th><th>REASONS</th><th>STATUS</th><th>REVIEW</th></tr></thead><tbody><tr *ngFor="let a of alerts"><td><span class="risk-score" [class.risk-high]="a.score>=0.7">{{a.score | percent:'1.0-1'}}</span></td><td>{{a.reasons}}</td><td><span class="state-badge state-pending">{{a.status}}</span></td><td><button class="text-button" (click)="reviewAlert(a.id, 'CLEARED')">Clear</button> <button class="text-button" (click)="reviewAlert(a.id, 'CONFIRMED')">Confirm</button></td></tr></tbody></table><div class="empty-inline" *ngIf="!alerts.length">No open fraud alerts.</div></div></section>
        <section class="panel staff-panel" *ngIf="tab==='admin'"><div class="panel-heading"><div><h2>Customer directory</h2><p>Registered NovaBank customers</p></div><span class="inbox-count">{{customers.length}} customers</span></div><div class="table-wrap"><table><thead><tr><th>CUSTOMER</th><th>EMAIL</th><th>VERIFICATION</th></tr></thead><tbody><tr *ngFor="let c of customers"><td><strong>{{c.fullName}}</strong></td><td>{{c.email}}</td><td><span class="state-badge">{{c.kycStatus}}</span></td></tr></tbody></table><div class="empty-inline" *ngIf="!customers.length">No customer records found.</div></div></section>
        <footer class="page-footer"><span>© 2026 NovaBank · Portfolio simulation</span><span>Protected by 256-bit encryption · <a href="mailto:support@novabank.local">Help & support</a></span></footer>
      </div>
    </main>
  </div>`
})
export class ShellComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  private router = inject(Router);
  tab = 'home'; menuOpen = false; busy = false;
  username = ''; accounts: any[] = [];
  chatInput = ''; chatBusy = false;
  chatSuggestions = ['What is my account balance?', 'Show my recent activity', 'Summarize my spending'];
  chatMessages: { role: 'assistant' | 'user'; text: string }[] = [{ role: 'assistant', text: 'Hi, I’m Nova AI. I can look up your recorded balances and recent transfers, summarize recorded outflows, or explain how this demo works. What would you like to know?' }]; notes: any[] = []; txns: any[] = []; alerts: any[] = []; customers: any[] = []; datasetOverview: any; fraudMetrics: any;
  statement: any; statementAccount = ''; error = ''; successMessage = ''; result: any;
  newType = 'SAVINGS'; fromId = ''; toId = ''; amount = '100.00'; channel = 'IMPS'; narration = 'Rent';
  filterAccount = ''; statusFilter = 'ALL'; fdAccount = ''; fdPrincipal = '5000'; fdRate = '7.1'; fdTenure = '12';
  loanAccount = ''; loanAmount = '50000'; loanTenure = '24'; loanPurpose = 'Education'; isStaff = false; isAdmin = false;
  private historyTimer?: ReturnType<typeof setTimeout>;
  private liveTimer?: ReturnType<typeof setInterval>;
  private transferAttempt?: { signature: string; key: string };
  lastUpdated = new Date();

  get totalBalance(): number { return this.accounts.reduce((sum, a) => sum + Number(a.balance || 0), 0); }
  get monthlyOutflows(): {label:string;amount:number}[] {
    const months = Array.from({length:6}, (_, i) => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - (5 - i)); return { key: `${d.getFullYear()}-${d.getMonth()}`, label: d.toLocaleString('en-IN', {month:'short'}) }; });
    const ids = new Set(this.accounts.map(a => Number(a.id)));
    return months.map(m => ({ label: m.label, amount: this.txns.filter(t => { const d = new Date(t.createdAt); return Number.isFinite(d.getTime()) && `${d.getFullYear()}-${d.getMonth()}` === m.key && ids.has(Number(t.debitAccountId)) && t.status === 'COMPLETED'; }).reduce((sum, t) => sum + Number(t.amount || 0), 0) }));
  }
  get displayAccounts(): any[] {
    if (this.accounts.length) return this.accounts;
    if (!this.isAdmin || !this.datasetOverview) return [];
    return (this.datasetOverview.sampleAccounts || []).map((a: any) => ({
      type: a.type, accountNumber: a.lastFour, balance: a.balanceInr, bank: a.bank,
      status: 'SYNTHETIC SAMPLE'
    }));
  }
  get dashboardTransactions(): any[] {
    if (!this.isAdmin || !this.datasetOverview) return this.txns;
    return (this.datasetOverview.recentActivity || []).map((t: any) => ({
      ...t, amount: t.amountInr, createdAt: t.date, channel: t.rail,
      direction: t.direction, narration: t.category, status: t.fraudFlagged ? 'SAMPLE FLAG' : 'SAMPLE'
    }));
  }
  get analyticsMonths(): {label:string;amount:number}[] {
    if (this.isAdmin && this.datasetOverview?.monthly) {
      return this.datasetOverview.monthly.map((m: any) => ({ label: m.month, amount: Number(m.volumeInr || 0) }));
    }
    return this.monthlyOutflows;
  }
  get recentSpend(): number { return this.analyticsMonths.reduce((sum, m) => sum + m.amount, 0); }
  get topChannel(): string { const totals = this.txns.filter(t => t.status === 'COMPLETED' && this.accounts.some(a => Number(a.id) === Number(t.debitAccountId))).reduce((sum: Record<string,number>, t) => { sum[t.channel] = (sum[t.channel] || 0) + Number(t.amount || 0); return sum; }, {}); return Object.entries(totals).sort((a,b) => b[1]-a[1])[0]?.[0] || ''; }
  barHeight(amount: number): number { const max = Math.max(...this.analyticsMonths.map(m => m.amount), 1); return amount > 0 ? Math.max(8, amount / max * 100) : 2; }
  get initials(): string { return this.username ? this.username.slice(0, 2).toUpperCase() : 'NB'; }
  get firstName(): string { return this.username ? this.username.split(/[ ._-]/)[0] : 'there'; }
  get greeting(): string { const hour = new Date().getHours(); return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'; }
  get pageTitle(): string { return ({ home: 'Overview', transfer: 'Transfers', history: 'Activity', products: 'Products', assistant: 'Nova AI', alerts: 'Risk monitor', admin: 'Customers' } as Record<string, string>)[this.tab] || 'Overview'; }
  get pageDescription(): string { return ({ transfer: 'Move money between accounts with confidence.', history: 'Search and review your account activity.', products: 'Explore savings and credit products.', assistant: 'A private guide to your recorded banking activity.', alerts: 'Review transfers flagged by the fraud detection service.', admin: 'Manage and review customer accounts.' } as Record<string, string>)[this.tab] || ''; }
  get filteredTransactions(): any[] { return this.txns.filter(t => this.statusFilter === 'ALL' || t.status === this.statusFilter); }
  ngOnInit() {
    if (!localStorage.getItem('accessToken')) { this.router.navigateByUrl('/login'); return; }
    let roles: string[] = [];
    try { roles = JSON.parse(localStorage.getItem('roles') || '[]'); } catch { localStorage.clear(); this.router.navigateByUrl('/login'); return; }
    this.isAdmin = roles.includes('ADMIN'); this.isStaff = this.isAdmin || roles.includes('FRAUD_ANALYST');
    this.api.me().subscribe({ next: u => this.username = u.username, error: () => this.logout() });
    if (this.isAdmin) this.api.demoOverview().subscribe({ next: data => this.datasetOverview = data, error: () => this.datasetOverview = undefined });
    if (this.isStaff) this.api.fraudMetrics().subscribe({ next: metrics => this.fraudMetrics = metrics, error: () => this.fraudMetrics = undefined });
    this.refresh();
    this.liveTimer = setInterval(() => this.refresh(), 15000);
  }
  ngOnDestroy() { if (this.liveTimer) clearInterval(this.liveTimer); if (this.historyTimer) clearTimeout(this.historyTimer); }
  go(tab: string) { this.tab = tab; this.menuOpen = false; if (tab === 'history') this.loadHistory(); }
  refresh() {
    this.lastUpdated = new Date();
    this.api.accounts().subscribe({ next: a => { this.accounts = a; if (!this.fromId && a.length) this.fromId = String(a[0].id); }, error: e => this.fail(e, 'Could not load accounts') });
    this.api.notifications().subscribe({ next: n => this.notes = n, error: () => this.notes = [] });
    this.loadHistory();
    if (this.isStaff) this.api.alerts().subscribe({ next: a => this.alerts = a, error: () => this.alerts = [] });
    if (this.isAdmin) this.api.customers().subscribe({ next: c => this.customers = c, error: () => this.customers = [] });
  }
  loadHistory() {
    if (this.historyTimer) clearTimeout(this.historyTimer);
    this.historyTimer = setTimeout(() => {
      const id = this.filterAccount.trim() && /^\d+$/.test(this.filterAccount.trim()) ? Number(this.filterAccount.trim()) : undefined;
      this.api.history(id).subscribe({ next: t => this.txns = t, error: e => this.fail(e, 'Could not load activity') });
      if (id) { this.statementAccount = String(id); this.loadStatement(); } else if (!this.filterAccount.trim()) this.statement = undefined;
    }, this.filterAccount ? 250 : 0);
  }
  loadStatement() { if (this.statementAccount) this.api.statement(Number(this.statementAccount)).subscribe({ next: s => this.statement = s, error: e => this.fail(e, 'Could not load statement') }); }
  open() { this.busy = true; this.api.openAccount(this.newType).subscribe({ next: () => { this.busy = false; this.flash('Account opened successfully.'); this.refresh(); }, error: e => { this.busy = false; this.fail(e, 'Could not open account'); } }); }
  send() {
    const source = this.accounts.find(a => String(a.id) === String(this.fromId));
    const cents = /^\d+(\.\d{1,2})?$/.test(this.amount.trim());
    if (!source || !/^\d+$/.test(this.toId.trim()) || !cents || Number(this.amount) <= 0 || String(source.id) === this.toId.trim()) { this.error = 'Choose a source account and enter a valid, different recipient account ID and amount.'; return; }
    this.error = ''; this.busy = true;
    const payload = { debitAccountId: Number(this.fromId), creditAccountId: Number(this.toId), amount: this.amount.trim(), channel: this.channel, narration: this.narration.trim() };
    const signature = JSON.stringify(payload);
    if (!this.transferAttempt || this.transferAttempt.signature !== signature) this.transferAttempt = { signature, key: crypto.randomUUID() };
    this.api.transfer(this.transferAttempt.key, payload).subscribe({ next: r => { this.busy = false; this.transferAttempt = undefined; this.result = r; this.flash(`Transfer ${r.status?.toLowerCase() || 'submitted'} successfully.`); this.refresh(); }, error: e => { this.busy = false; this.fail(e, 'Transfer failed'); } });
  }
  sendChat(suggested?: string) {
    const message = (suggested ?? this.chatInput).trim();
    if (!message || this.chatBusy) return;
    this.chatMessages.push({ role: 'user', text: message }); this.chatInput = ''; this.chatBusy = true;
    this.api.assistantChat(message).subscribe({ next: reply => { this.chatMessages.push({ role: 'assistant', text: reply.message }); this.chatBusy = false; }, error: e => { this.chatMessages.push({ role: 'assistant', text: e?.error?.message || 'I can’t reach the assistant right now. Please try again in a moment.' }); this.chatBusy = false; } });
  }
  reviewAlert(id: number, status: string) { this.api.reviewAlert(id, status).subscribe({ next: () => { this.flash(`Alert marked ${status.toLowerCase()}.`); this.api.alerts().subscribe({ next: a => this.alerts = a, error: () => this.alerts = [] }); }, error: e => this.fail(e, "Could not update alert") }); }
  openFd() { this.busy = true; this.api.fd({ accountId: Number(this.fdAccount), principal: this.fdPrincipal, rate: this.fdRate, tenureMonths: Number(this.fdTenure) }).subscribe({ next: () => { this.busy = false; this.flash('Fixed deposit request submitted.'); }, error: e => { this.busy = false; this.fail(e, 'Could not book fixed deposit'); } }); }
  applyLoan() { this.busy = true; this.api.loan({ accountId: Number(this.loanAccount), amount: this.loanAmount, tenureMonths: Number(this.loanTenure), purpose: this.loanPurpose.trim() }).subscribe({ next: () => { this.busy = false; this.flash('Loan application submitted for review.'); }, error: e => { this.busy = false; this.fail(e, 'Could not submit loan application'); } }); }
  isIncoming(t: any): boolean { return t.direction === 'CREDIT' || t.creditAccountId === Number(this.filterAccount); }
  maskAccount(value: string): string { const text = String(value || ''); return text.length > 4 ? `•••• ${text.slice(-4)}` : text; }
  flash(message: string) { this.successMessage = message; setTimeout(() => this.successMessage = '', 4500); }
  fail(e: any, fallback: string) { this.error = e?.error?.message || fallback; }
  logout() { localStorage.clear(); this.router.navigateByUrl('/login'); }
  registerCustomer() { this.logout(); }
}
