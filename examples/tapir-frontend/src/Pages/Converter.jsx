import { useForm } from '@inertiajs/react'

export default function Converter({ input, cborHex, cborBase64 }) {
  const { data, setData, post, processing, errors } = useForm({ jsonInput: input || '' })

  function handleSubmit(e) {
    e.preventDefault()
    post('/convert')
  }

  return (
    <div style={{ maxWidth: 720, margin: '2rem auto', fontFamily: 'system-ui' }}>
      <h1>JSON → CBOR Converter</h1>
      <p style={{ color: '#666' }}>
        Powered by <strong>inertia-tapir</strong> + <a href="https://github.com/sirthias/borer">borer</a>
      </p>

      <form onSubmit={handleSubmit}>
        <label htmlFor="jsonInput" style={{ fontWeight: 'bold', display: 'block', marginBottom: '0.3rem' }}>
          JSON Input
        </label>
        <textarea
          id="jsonInput"
          value={data.jsonInput}
          onChange={e => setData('jsonInput', e.target.value)}
          rows={8}
          style={{
            width: '100%',
            fontFamily: 'monospace',
            fontSize: '0.9rem',
            padding: '0.5rem',
            boxSizing: 'border-box',
            borderRadius: 4,
            border: '1px solid #ccc',
          }}
          placeholder='{"key": "value"}'
        />
        <button
          type="submit"
          disabled={processing || !data.jsonInput.trim()}
          style={{
            marginTop: '0.5rem',
            padding: '0.5rem 1.5rem',
            fontSize: '1rem',
            cursor: 'pointer',
          }}
        >
          Convert to CBOR
        </button>
      </form>

      {errors.jsonInput && (
        <div style={{
          marginTop: '1rem',
          padding: '0.75rem',
          background: '#fee',
          border: '1px solid #c66',
          borderRadius: 4,
          color: '#900',
        }}>
          {errors.jsonInput}
        </div>
      )}

      {cborHex && (
        <div style={{ marginTop: '1.5rem' }}>
          <h2>Result</h2>

          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '0.3rem' }}>
              CBOR (hex)
            </label>
            <textarea
              readOnly
              value={cborHex}
              rows={4}
              style={{
                width: '100%',
                fontFamily: 'monospace',
                fontSize: '0.85rem',
                padding: '0.5rem',
                boxSizing: 'border-box',
                borderRadius: 4,
                border: '1px solid #ccc',
                background: '#f5f5f5',
                wordBreak: 'break-all',
              }}
            />
          </div>

          <div>
            <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '0.3rem' }}>
              CBOR (Base64)
            </label>
            <textarea
              readOnly
              value={cborBase64}
              rows={3}
              style={{
                width: '100%',
                fontFamily: 'monospace',
                fontSize: '0.85rem',
                padding: '0.5rem',
                boxSizing: 'border-box',
                borderRadius: 4,
                border: '1px solid #ccc',
                background: '#f5f5f5',
                wordBreak: 'break-all',
              }}
            />
          </div>

          <p style={{ color: '#666', fontSize: '0.85rem', marginTop: '0.5rem' }}>
            {cborHex.length / 2} bytes (JSON was {new TextEncoder().encode(data.jsonInput).length} bytes)
          </p>
        </div>
      )}
    </div>
  )
}
