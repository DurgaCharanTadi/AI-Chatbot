import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';


@Pipe({name:'linkify', standalone:true})
export class LinkifyPipe implements PipeTransform{
    constructor(private s: DomSanitizer){}
    
    transform(value: string): SafeHtml{
        if(!value) return '';
        const url = /(https?:\/\/[^\s)]+)|(www\.[^\s)]+)/gi;
        const html = value.replace(url, (m)=>{
        const href = m.startsWith('http')? m : `https://${m}`;
        return `<a href="${href}" target="_blank" rel="noopener">${m}</a>`;
        })
        .replace(/\n/g,'<br/>');
        return this.s.bypassSecurityTrustHtml(html);
    }
}