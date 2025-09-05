import { Component, signal } from '@angular/core';
//import { RouterOutlet } from '@angular/router';
import { ChatShell } from './components/chat-shell/chat-shell';
import { Header } from './components/header/header';

@Component({
  selector: 'app-root',
  imports: [ChatShell, Header],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  zoom = 0.95; 
  protected readonly title = signal('ai-chatbot');
}
