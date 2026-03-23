import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import sql from 'highlight.js/lib/languages/sql'
import css from 'highlight.js/lib/languages/css'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('css', css)

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(code, { language: lang }).value } catch {}
    }
    return code
  }
})

export function renderMarkdown(text) {
  if (!text) return ''
  try {
    let html = marked.parse(text)
    // Wrap pre blocks with copy button
    html = html.replace(/<pre><code(.*?)>([\s\S]*?)<\/code><\/pre>/g,
      '<div class="code-block-wrapper"><pre><code$1>$2</code></pre><button class="code-copy-btn" onclick="navigator.clipboard.writeText(this.parentElement.querySelector(\'pre\').textContent).then(()=>{this.textContent=\'已复制\';setTimeout(()=>this.textContent=\'复制\',1500)})">复制</button></div>')
    return html
  } catch {
    return escapeHtml(text)
  }
}

export function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}
