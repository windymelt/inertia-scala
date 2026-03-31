import { Link } from '@inertiajs/react'

export default function About({ version }) {
  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>About</h1>
      <p>inertia-scala v{version}</p>
      <p>
        A Scala 3 server-side adapter for{' '}
        <a href="https://inertiajs.com" target="_blank" rel="noreferrer">Inertia.js</a>.
      </p>
      <p><Link href="/">&larr; Home</Link></p>
    </div>
  )
}
