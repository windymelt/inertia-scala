import { Link, router, usePage } from '@inertiajs/react'
import { useState } from 'react'

export default function UsersShow({ user, posts }) {
  // error bag を使うと、各フォームのエラーが props.errors.<bag> に分離して返る。
  // useForm はエラーを自動でフォームにスコープするため、ここでは error bag の
  // 動作を明示するためにあえて router を直接使う。
  const { errors } = usePage().props
  const profileErrors = errors.updateProfile || {}
  const passwordErrors = errors.updatePassword || {}

  const [profile, setProfile] = useState({ name: user.name, email: user.email })
  const [password, setPassword] = useState({ password: '', passwordConfirmation: '' })

  function submitProfile(e) {
    e.preventDefault()
    router.post(`/users/${user.id}/profile`, profile, { errorBag: 'updateProfile' })
  }

  function submitPassword(e) {
    e.preventDefault()
    router.post(`/users/${user.id}/password`, password, {
      errorBag: 'updatePassword',
      onSuccess: () => setPassword({ password: '', passwordConfirmation: '' }),
    })
  }

  const errStyle = { color: '#c00', fontSize: '0.85rem', margin: '0.2rem 0 0' }
  const fieldStyle = { display: 'block', width: '100%', padding: '0.4rem', boxSizing: 'border-box' }

  return (
    <div style={{ maxWidth: 600, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>{user.name}</h1>
      <p>{user.email}</p>

      {/* フォーム1: プロフィール更新（error bag: updateProfile） */}
      <h2>プロフィール更新</h2>
      <form onSubmit={submitProfile} style={{ display: 'grid', gap: '0.5rem', marginBottom: '1.5rem' }}>
        <div>
          <input
            type="text"
            value={profile.name}
            onChange={e => setProfile({ ...profile, name: e.target.value })}
            placeholder="名前"
            style={fieldStyle}
          />
          {profileErrors.name && <p style={errStyle}>{profileErrors.name}</p>}
        </div>
        <div>
          <input
            type="text"
            value={profile.email}
            onChange={e => setProfile({ ...profile, email: e.target.value })}
            placeholder="メールアドレス"
            style={fieldStyle}
          />
          {profileErrors.email && <p style={errStyle}>{profileErrors.email}</p>}
        </div>
        <button type="submit" style={{ justifySelf: 'start' }}>更新</button>
      </form>

      {/* フォーム2: パスワード変更（error bag: updatePassword） */}
      <h2>パスワード変更</h2>
      <form onSubmit={submitPassword} style={{ display: 'grid', gap: '0.5rem', marginBottom: '1.5rem' }}>
        <div>
          <input
            type="password"
            value={password.password}
            onChange={e => setPassword({ ...password, password: e.target.value })}
            placeholder="新しいパスワード"
            style={fieldStyle}
          />
          {passwordErrors.password && <p style={errStyle}>{passwordErrors.password}</p>}
        </div>
        <div>
          <input
            type="password"
            value={password.passwordConfirmation}
            onChange={e => setPassword({ ...password, passwordConfirmation: e.target.value })}
            placeholder="パスワード（確認）"
            style={fieldStyle}
          />
          {passwordErrors.passwordConfirmation && <p style={errStyle}>{passwordErrors.passwordConfirmation}</p>}
        </div>
        <button type="submit" style={{ justifySelf: 'start' }}>変更</button>
      </form>

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
