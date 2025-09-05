import { Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AutosizeDirective } from '../../directives/autosize.directive';
import { Chat } from '../../services/chat';

interface Command {
  key: string;
  description: string;
}

@Component({
  selector: 'app-omnibox',
  standalone: true,
  imports: [CommonModule, FormsModule, AutosizeDirective],
  templateUrl: './omnibox.html',
  styleUrl: './omnibox.css'
})
export class Omnibox {
  text = '';
  mode: 'chat' | 'search' = 'chat';
  showCmds = false;
  showEmoji = false;
  files: File[] = [];


commands: Command[] = [
  { key:'/new', description:'Start a new topic (clear chat)' },
  { key:'/stop', description:'Stop current response' },
  { key:'/regenerate', description:'Regenerate last reply' }
];


  @ViewChild('ta') ta!: ElementRef<HTMLTextAreaElement>;

  constructor(public chat: Chat) { }

  onEnter(e: Event) {
    if (!(e instanceof KeyboardEvent)) return;
    if (e.shiftKey) return;
    e.preventDefault();
    this.submit();
  }
  onInput() {
    this.showCmds = this.text.trim().startsWith('/');
  }
  attach(change: Event) {
    const input = change.target as HTMLInputElement;
    const selected = Array.from(input.files ?? []);
    this.files = [...this.files, ...selected];
    input.value = '';
  }
  removeFile(ix: number) { this.files.splice(ix, 1); }


  submit() {
    const val = this.text.trim();
    if (!val && this.files.length === 0) return;

    // Handle slash commands
    if (val.startsWith('/')) {
      const [cmd, ...rest] = val.split(' ');
      const arg = rest.join(' ').trim();
      switch (cmd) {
        case '/new': this.chat.clear(); break;
        case '/stop': this.chat.stop(); break;
        case '/regenerate': this.chat.regenerate(); break;
      }
    } else {
        this.chat.send(val, this.files);
    }


    this.text = '';
    this.files = [];
    this.showCmds = false; 

    // Reset the textarea height to its initial autosize baseline
    const ta = this.ta?.nativeElement;
    if (ta) {
      // Wait a frame so the DOM reflects the cleared value
      requestAnimationFrame(() => {
        ta.style.height = 'auto';
        ta.style.height = Math.min(ta.scrollHeight, 240) + 'px';
      });
    }
  }

}