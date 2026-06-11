import { Link, useForm, router } from '@inertiajs/react'

export default function TodosIndex({ todos }) {
  const { data, setData, post, processing, errors } = useForm({ title: '' })

  function handleSubmit(e) {
    e.preventDefault()
    post('/todos', { onSuccess: () => setData('title', '') })
  }

  function toggle(id) {
    router.post(`/todos/${id}/toggle`)
  }

  function remove(id) {
    router.post(`/todos/${id}/delete`)
  }

  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>Todos</h1>

      <form onSubmit={handleSubmit} style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            type="text"
            value={data.title}
            onChange={e => setData('title', e.target.value)}
            placeholder="New todo..."
            style={{ flex: 1, padding: '0.4rem', borderColor: errors.title ? '#c66' : undefined }}
          />
          {/* サーバー側バリデーションを実演するため、空でもボタンは押せるようにしている */}
          <button type="submit" disabled={processing}>
            Add
          </button>
        </div>
        {errors.title && (
          <p style={{ color: '#c00', fontSize: '0.85rem', margin: '0.3rem 0 0' }}>
            {errors.title}
          </p>
        )}
      </form>

      <ul style={{ listStyle: 'none', padding: 0 }}>
        {todos.map(todo => (
          <li key={todo.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.3rem 0' }}>
            <input
              type="checkbox"
              checked={todo.done}
              onChange={() => toggle(todo.id)}
            />
            <span style={{ flex: 1, textDecoration: todo.done ? 'line-through' : 'none', color: todo.done ? '#999' : 'inherit' }}>
              {todo.title}
            </span>
            <button onClick={() => remove(todo.id)} style={{ color: 'red', border: 'none', background: 'none', cursor: 'pointer' }}>
              ✕
            </button>
          </li>
        ))}
      </ul>

      <p style={{ color: '#666', fontSize: '0.9rem' }}>
        {todos.filter(t => !t.done).length} remaining / {todos.length} total
      </p>

      <p><Link href="/">&larr; Home</Link></p>
    </div>
  )
}
