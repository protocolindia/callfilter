import React, { useRef, useEffect } from 'react';

/**
 * Minimal rich-text editor with no external dependencies. Produces HTML.
 * Uses document.execCommand (still supported across browsers) for basic
 * formatting suitable for email templates.
 *
 * Props: value (html string), onChange(html), placeholders (array of tokens)
 */
export default function RichTextEditor({ value, onChange, placeholders = [] }) {
  const ref = useRef(null);

  // Initialize / sync external value without clobbering the caret while typing.
  useEffect(() => {
    if (ref.current && ref.current.innerHTML !== (value || '')) {
      ref.current.innerHTML = value || '';
    }
  }, [value]);

  const exec = (cmd, arg) => {
    document.execCommand(cmd, false, arg);
    ref.current && ref.current.focus();
    emit();
  };

  const emit = () => { if (ref.current && onChange) onChange(ref.current.innerHTML); };

  const insertToken = (token) => {
    document.execCommand('insertText', false, token);
    emit();
  };

  const btn = {
    padding: '5px 9px', borderRadius: 5, border: '1px solid var(--border)',
    background: 'var(--surface)', color: 'var(--text)', cursor: 'pointer',
    fontSize: 13, fontWeight: 600,
  };

  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, padding: 8,
          background: 'var(--card)', borderBottom: '1px solid var(--border)' }}>
        <button type="button" style={btn} onClick={() => exec('bold')}><b>B</b></button>
        <button type="button" style={btn} onClick={() => exec('italic')}><i>I</i></button>
        <button type="button" style={btn} onClick={() => exec('underline')}><u>U</u></button>
        <button type="button" style={btn} onClick={() => exec('insertUnorderedList')}>• List</button>
        <button type="button" style={btn} onClick={() => exec('insertOrderedList')}>1. List</button>
        <button type="button" style={btn} onClick={() => exec('formatBlock', 'H3')}>H</button>
        <button type="button" style={btn} onClick={() => {
          const url = window.prompt('Link URL:'); if (url) exec('createLink', url);
        }}>🔗 Link</button>
        <button type="button" style={btn} onClick={() => exec('removeFormat')}>Clear</button>
      </div>

      {placeholders.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, padding: '6px 8px',
            background: 'var(--card)', borderBottom: '1px solid var(--border)' }}>
          <span className="muted" style={{ fontSize: 12, alignSelf: 'center' }}>Insert:</span>
          {placeholders.map(p => (
            <button type="button" key={p} onClick={() => insertToken('{{' + p + '}}')}
              style={{ ...btn, fontSize: 12, fontWeight: 500 }}>{'{{' + p + '}}'}</button>
          ))}
        </div>
      )}

      <div
        ref={ref}
        contentEditable
        onInput={emit}
        onBlur={emit}
        style={{ minHeight: 160, padding: 12, outline: 'none',
          background: 'var(--surface)', color: 'var(--text)', fontSize: 14, lineHeight: 1.5 }}
        suppressContentEditableWarning
      />
    </div>
  );
}
