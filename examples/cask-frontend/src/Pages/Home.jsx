import { Link } from '@inertiajs/react'

export default function Home({ greeting, userCount }) {
  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>{greeting}</h1>
      <p>There are <strong>{userCount}</strong> users.</p>
      <nav>
        <ul>
          <li><Link href="/todos">Todos</Link></li>
          <li><Link href="/users">Users</Link></li>
          <li><Link href="/about">About</Link></li>
        </ul>
      </nav>
    </div>
  )
}
