import { Link } from '@inertiajs/react'

export default function UsersShow({ user, posts }) {
  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>{user.name}</h1>
      <p>{user.email}</p>

      <h2>Posts</h2>
      {posts.length === 0 ? (
        <p>No posts yet.</p>
      ) : (
        <ul>
          {posts.map(post => (
            <li key={post.id}>
              <strong>{post.title}</strong>
              <p>{post.body}</p>
            </li>
          ))}
        </ul>
      )}

      <p><Link href="/users">&larr; All Users</Link></p>
    </div>
  )
}
