import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login.component';
import { ShellComponent } from './pages/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: ShellComponent },
  { path: '**', redirectTo: '' }
];
