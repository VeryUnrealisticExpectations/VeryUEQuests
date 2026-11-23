import type {MouseEvent, PointerEvent} from 'react'
import {useId, useRef, useState} from 'react'
import type {ComponentTextContext, Quest} from '../types.ts'
import {ComponentText} from './ComponentText.tsx'
import {plainComponentText} from '../lib/components.ts'
import {QuestNodeIcon} from './QuestNodeIcon.tsx'
import {type CrossSectionDependency, QuestDependencyTooltip} from './QuestDependencyTooltip.tsx'

export function QuestNode({
                              quest,
                              dependencies,
                              selected,
                              prerequisite,
                              connecting,
                              disabled,
                              epoch,
                              textContext,
                              onPointerDown,
                              onContextMenu,
                              onSelect,
                              onEdit
                          }:
                          {
                              quest: Quest;
                              dependencies: CrossSectionDependency[];
                              selected: boolean;
                              prerequisite: boolean;
                              connecting: boolean;
                              disabled: boolean;
                              epoch: number;
                              textContext: ComponentTextContext;
                              onPointerDown: (event: PointerEvent<HTMLButtonElement>) => void;
                              onContextMenu: (event: MouseEvent<HTMLButtonElement>) => void;
                              onSelect: (event?: MouseEvent<HTMLButtonElement>) => void;
                              onEdit: (event?: MouseEvent<HTMLButtonElement>) => void
                          }) {
    const title = plainComponentText(quest.display.title, textContext)
    const tooltipId = useId()
    const lock = useRef<HTMLSpanElement>(null)
    const [tooltipAnchor, setTooltipAnchor] = useState<HTMLSpanElement>()
    const showTooltip = !!tooltipAnchor && dependencies.length > 0
    return <>
        <button type="button"
                className={`quest-node${selected ? ' selected' : ''}${prerequisite ? ' connection-source' : ''}`}
                disabled={disabled} style={{left: quest.position.x, top: quest.position.y}} aria-label={title}
                aria-pressed={selected}
                aria-describedby={showTooltip ? tooltipId : undefined} title={quest.display.tooltip || title}
                onPointerDown={event => {
                    setTooltipAnchor(undefined);
                    onPointerDown(event)
                }}
                onContextMenu={onContextMenu} onClick={onSelect} onDoubleClick={onEdit}
                onFocus={event => {
                    if (event.currentTarget.matches(':focus-visible') && lock.current) {
                        setTooltipAnchor(lock.current)
                    }
                }}
                onBlur={() => setTooltipAnchor(undefined)}
                onKeyDown={event => {
                    if (event.key === 'Escape') {
                        setTooltipAnchor(undefined)
                    } else if (event.key === 'Enter') {
                        event.preventDefault()
                        if (connecting) {
                            onSelect()
                        } else {
                            onEdit()
                        }
                    }
                }}>
            <QuestNodeIcon display={quest.display} epoch={epoch} revision={textContext.assetRevision}/>
            {dependencies.length > 0 &&
                <span ref={lock} className="quest-dependency-lock" title="" data-highlighted={showTooltip || undefined}
                      onPointerEnter={event => setTooltipAnchor(event.currentTarget)}
                      onPointerLeave={() => setTooltipAnchor(undefined)}>
        <svg viewBox="0 0 16 16" width="16" height="16" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"
             focusable="false">
          <path fill="currentColor" fillRule="evenodd" clipRule="evenodd"
                d="M4 6V4C4 1.79086 5.79086 0 8 0C10.2091 0 12 1.79086 12 4V6H14V16H2V6H4ZM6 4C6 2.89543 6.89543 2 8 2C9.10457 2 10 2.89543 10 4V6H6V4ZM7 13V9H9V13H7Z"/>
        </svg>
      </span>}
            <span className="quest-label"><ComponentText value={quest.display.title} textContext={textContext}
                                                         useInterfaceFont/></span>
        </button>
        {tooltipAnchor && showTooltip &&
            <QuestDependencyTooltip id={tooltipId} anchor={tooltipAnchor} dependencies={dependencies}
                                    textContext={textContext}/>}
    </>
}
