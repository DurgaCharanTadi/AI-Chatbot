import { Directive, ElementRef, HostListener } from '@angular/core';

@Directive({
    selector:'[autosize]', 
    standalone:true
})

export class AutosizeDirective{

    constructor(private el: ElementRef<HTMLTextAreaElement>){}

    ngAfterViewInit(){ this.resize(); }

    @HostListener('input') onInput(){ this.resize(); }

    private resize(){
        const ta = this.el.nativeElement;
        ta.style.height = 'auto';
        ta.style.height = Math.min(ta.scrollHeight, 240) + 'px';
    }
}