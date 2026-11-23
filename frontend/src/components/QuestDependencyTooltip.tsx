import {useLayoutEffect, useRef} from 'react'
import {createPortal} from 'react-dom'
import type {ChatComponent, ComponentTextContext} from '../types.ts'
import {ComponentText} from './ComponentText.tsx'

export interface CrossSectionDependency {
    id: string
    section: ChatComponent
    title: ChatComponent
}

const GAP = 8

export function QuestDependencyTooltip({id, anchor, dependencies, textContext}:
                                       {
                                           id: string;
                                           anchor: HTMLSpanElement;
                                           dependencies: CrossSectionDependency[];
                                           textContext: ComponentTextContext
                                       }) {
    const tooltip = useRef<HTMLDivElement>(null)
    useLayoutEffect(() => {
        const element = tooltip.current!
        const bounds = anchor.getBoundingClientRect()
        const {offsetWidth, offsetHeight} = element
        const right = bounds.right + GAP
        const maxLeft = window.innerWidth - offsetWidth - GAP
        const left = right <= maxLeft ? right : bounds.left - offsetWidth - GAP
        element.style.left = `${Math.max(GAP, Math.min(left, maxLeft))}px`
        element.style.top = `${Math.max(GAP, Math.min(bounds.top, window.innerHeight - offsetHeight - GAP))}px`
    })
    return createPortal(<div ref={tooltip} id={id} className="quest-dependency-tooltip" role="tooltip">
        <strong>Dependencies</strong>
        <ul>{dependencies.map(dependency => <li key={dependency.id}>
            <ComponentText value={dependency.section} textContext={textContext} useInterfaceFont/>{': '}
            <ComponentText value={dependency.title} textContext={textContext} useInterfaceFont/>
        </li>)}</ul>
    </div>, document.body)
}
