import { Link } from '@inertiajs/react'

export default function UsersIndex({ users }) {
  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>Users</h1>
      <ul>
        {users.map(user => (
          <li key={user.id}>
            <Link href={`/users/${user.id}`}>{user.name}</Link>
            {' '}&mdash; {user.email}
          </li>
        ))}
      </ul>
      <p><Link href="/">&larr; Home</Link></p>
    </div>
  )
}
