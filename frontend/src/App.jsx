import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const apiBase = import.meta.env.VITE_API_BASE_URL ?? ''

function profileImageSrc(url) {
  if (!url) return ''
  return url.startsWith('http://') || url.startsWith('https://') ? url : `${apiBase}${url}`
}

async function request(path, options) {
  const response = await fetch(`${apiBase}${path}`, { credentials: 'include', ...options })
  if (!response.ok) {
    const error = new Error(`Request failed: ${response.status}`)
    error.status = response.status
    throw error
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return null
  return response.json()
}

export default function App() {
  const clientRef = useRef(null)
  const profileImageInputRef = useRef(null)
  const attachmentInputRef = useRef(null)
  const messagesRef = useRef(null)
  const [rooms, setRooms] = useState([])
  const [devNickname, setDevNickname] = useState('')
  const [currentUser, setCurrentUser] = useState(null)
  const [nickname, setNickname] = useState('')
  const [loggedIn, setLoggedIn] = useState(false)
  const [authLoading, setAuthLoading] = useState(true)
  const [currentRoom, setCurrentRoom] = useState(null)
  const [messages, setMessages] = useState([])
  const [members, setMembers] = useState([])
  const [onlineMembers, setOnlineMembers] = useState([])
  const [message, setMessage] = useState('')
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState('')
  const [friends, setFriends] = useState([])
  const [friendCodeInput, setFriendCodeInput] = useState('')
  const [groupName, setGroupName] = useState('')
  const [selectedFriendIds, setSelectedFriendIds] = useState([])
  const [groupModalOpen, setGroupModalOpen] = useState(false)
  const [friendContextMenu, setFriendContextMenu] = useState(null)
  const [roomContextMenu, setRoomContextMenu] = useState(null)
  const [profileModalOpen, setProfileModalOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [profileFile, setProfileFile] = useState(null)
  const [profilePreview, setProfilePreview] = useState('')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [theme, setTheme] = useState(() => window.localStorage.getItem('chat-theme') || 'light')
  const [reactions, setReactions] = useState({})
  const [reactionContextMenu, setReactionContextMenu] = useState(null)
  const [uploadingAttachment, setUploadingAttachment] = useState(false)

  const loadRooms = useCallback(async () => {
    try {
      if (!nickname) return
      setRooms(await request(`/chat/rooms?nickname=${encodeURIComponent(nickname)}`))
      setError((current) => current === '채팅방 목록을 불러오지 못했습니다.' ? '' : current)
    }
    catch { setError('채팅방 목록을 불러오지 못했습니다.') }
  }, [nickname])

  const loadFriends = useCallback(async () => {
    if (!loggedIn) return
    try { setFriends(await request('/friends')) }
    catch { setError('친구 목록을 불러오지 못했습니다.') }
  }, [loggedIn])

  const loadNotifications = useCallback(async () => {
    const recipient = nickname.trim()
    if (!recipient) {
      setNotifications([])
      return
    }
    try {
      setNotifications(await request(`/notifications?recipient=${encodeURIComponent(recipient)}`))
    } catch {
      setError('알림을 불러오지 못했습니다.')
    }
  }, [nickname])

  const loadMembers = useCallback(async (roomId) => {
    const [all, online] = await Promise.all([
      request(`/chat/room/${roomId}/members`),
      request(`/chat/room/${roomId}/online-members`),
    ])
    setMembers(all)
    setOnlineMembers(online)
  }, [])

  useEffect(() => {
    if (!loggedIn) return undefined
    loadRooms()
    const timer = window.setInterval(loadRooms, 5000)
    return () => window.clearInterval(timer)
  }, [loadRooms, loggedIn])

  useEffect(() => {
    loadNotifications()
    const timer = window.setInterval(loadNotifications, 5000)
    return () => window.clearInterval(timer)
  }, [loadNotifications])

  useEffect(() => {
    if (!loggedIn) return undefined
    loadFriends()
    const timer = window.setInterval(loadFriends, 5000)
    return () => window.clearInterval(timer)
  }, [loadFriends, loggedIn])

  useEffect(() => {
    if (!loggedIn) return undefined
    const sendHeartbeat = () => request('/presence/heartbeat', { method: 'POST' }).catch(() => {})
    sendHeartbeat()
    const timer = window.setInterval(sendHeartbeat, 10000)
    return () => window.clearInterval(timer)
  }, [loggedIn])

  useEffect(() => {
    function closeContextMenus() {
      setFriendContextMenu(null)
      setRoomContextMenu(null)
      setReactionContextMenu(null)
    }
    window.addEventListener('click', closeContextMenus)
    window.addEventListener('blur', closeContextMenus)
    return () => {
      window.removeEventListener('click', closeContextMenus)
      window.removeEventListener('blur', closeContextMenus)
    }
  }, [])

  useEffect(() => () => { clientRef.current?.deactivate() }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('chat-theme', theme)
  }, [theme])

  useEffect(() => {
    if (!currentRoom) {
      setReactions({})
      return undefined
    }
    const load = async () => {
      try {
        const list = await request(`/chat/room/${currentRoom.roomId}/reactions`)
        setReactions(Object.fromEntries(list.map((item) => [item.messageId, item])))
      } catch { /* 다음 갱신에서 재시도 */ }
    }
    load()
    const timer = window.setInterval(load, 3000)
    return () => window.clearInterval(timer)
  }, [currentRoom])

  useEffect(() => {
    const container = messagesRef.current
    if (!container) return
    window.requestAnimationFrame(() => { container.scrollTop = container.scrollHeight })
  }, [messages.length, currentRoom?.roomId])

  useEffect(() => {
    window.history.replaceState({ view: 'login' }, '')
    request('/auth/me')
      .then((user) => {
        setCurrentUser(user)
        setNickname(user.nickname)
        setLoggedIn(true)
        window.history.replaceState({ view: 'lobby' }, '')
      })
      .catch(() => {})
      .finally(() => setAuthLoading(false))
  }, [])

  function socialLogin(provider) {
    window.location.href = `${apiBase}/oauth2/authorization/${provider}`
  }

  async function devLogin(event) {
    event.preventDefault()
    const value = devNickname.trim()
    if (!value) {
      setError('테스트용 닉네임을 입력하세요.')
      return
    }
    try {
      const user = await request(`/auth/dev-login?nickname=${encodeURIComponent(value)}`, { method: 'POST' })
      setCurrentUser(user)
      setNickname(user.nickname)
      setLoggedIn(true)
      setError('')
      window.history.pushState({ view: 'lobby' }, '')
    } catch {
      setError('개발용 로그인에 실패했습니다. 다른 닉네임을 사용해 보세요.')
    }
  }

  async function logout() {
    await request('/presence', { method: 'DELETE' }).catch(() => {})
    await request('/logout', { method: 'POST' }).catch(() => {})
    setLoggedIn(false)
    setCurrentUser(null)
    setNickname('')
    setNotifications([])
    window.history.replaceState({ view: 'login' }, '')
  }

  function openProfileModal() {
    setProfileName(nickname)
    setProfileFile(null)
    setProfilePreview(profileImageSrc(currentUser?.profileImageUrl))
    setError('')
    setProfileModalOpen(true)
  }

  function selectProfileImage(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) {
      setError('프로필 사진은 5MB 이하 이미지 파일만 사용할 수 있습니다.')
      return
    }
    setProfileFile(file)
    setProfilePreview(URL.createObjectURL(file))
  }

  async function saveProfile(event) {
    event.preventDefault()
    const cleanName = profileName.trim()
    if (!cleanName) return setError('이름을 입력하세요.')
    try {
      let updatedUser = currentUser
      if (cleanName !== nickname) {
        updatedUser = await request('/auth/profile', {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ nickname: cleanName }),
        })
      }
      if (profileFile) {
        const formData = new FormData()
        formData.append('image', profileFile)
        const imageResult = await request('/auth/profile-image', { method: 'POST', body: formData })
        updatedUser = { ...updatedUser, profileImageUrl: imageResult.profileImageUrl }
      }
      await clientRef.current?.deactivate()
      clientRef.current = null
      setCurrentUser(updatedUser)
      setNickname(updatedUser.nickname)
      setCurrentRoom(null)
      setMessages([])
      setMembers([])
      setOnlineMembers([])
      setProfileModalOpen(false)
      setError('')
    } catch (requestError) {
      setError(requestError.status === 409 ? '이미 사용 중인 이름입니다.' : '프로필을 변경하지 못했습니다.')
    }
  }

  async function addFriend(event) {
    event.preventDefault()
    if (!friendCodeInput.trim()) return
    try {
      await request(`/friends/code?code=${encodeURIComponent(friendCodeInput.trim())}`, { method: 'POST' })
      setFriendCodeInput('')
      setError('')
      await loadFriends()
    } catch (requestError) {
      if (requestError.status === 401) {
        setError('로그인이 만료되었습니다. 로그아웃 후 다시 로그인해 주세요.')
      } else if (requestError.status === 404) {
        setError('해당 친구 코드를 찾을 수 없습니다.')
      } else if (requestError.status === 400) {
        setError('자기 자신의 코드는 추가할 수 없습니다.')
      } else {
        setError('친구를 추가하지 못했습니다.')
      }
    }
  }

  async function startDirectChat(friend) {
    try {
      const room = await request(`/chat/direct/${friend.id}`, { method: 'POST' })
      await loadRooms()
      await enterRoom(room)
    } catch { setError('1:1 대화를 시작하지 못했습니다.') }
  }

  function openFriendContextMenu(event, friend) {
    event.preventDefault()
    setFriendContextMenu({
      friend,
      x: Math.min(event.clientX, window.innerWidth - 170),
      y: Math.min(event.clientY, window.innerHeight - 60),
    })
  }

  async function deleteFriend(friend) {
    setFriendContextMenu(null)
    if (!window.confirm(`${friend.nickname}님을 친구 목록에서 삭제할까요?`)) return
    try {
      await request(`/friends/${friend.id}`, { method: 'DELETE' })
      setFriends((items) => items.filter((item) => item.id !== friend.id))
      setSelectedFriendIds((ids) => ids.filter((id) => id !== friend.id))
      setError('')
    } catch (requestError) {
      if (requestError.status === 404) {
        setFriends((items) => items.filter((item) => item.id !== friend.id))
        setSelectedFriendIds((ids) => ids.filter((id) => id !== friend.id))
        setError('')
      } else if (requestError.status === 401) {
        setLoggedIn(false)
        setCurrentUser(null)
        setNickname('')
        setFriends([])
        setNotifications([])
        setError('로그인이 만료되었습니다. 다시 로그인해 주세요.')
        window.history.replaceState({ view: 'login' }, '')
      } else {
        setError(`친구를 삭제하지 못했습니다. (오류 ${requestError.status ?? '연결'})`)
      }
    }
  }

  function toggleGroupFriend(friendId) {
    setSelectedFriendIds((ids) => ids.includes(friendId) ? ids.filter((id) => id !== friendId) : [...ids, friendId])
  }

  async function createGroupChat(event) {
    event.preventDefault()
    if (!groupName.trim() || !selectedFriendIds.length) return setError('단체방 이름과 초대할 친구를 선택하세요.')
    try {
      const room = await request('/chat/group', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: groupName.trim(), friendIds: selectedFriendIds }),
      })
      setGroupName('')
      setSelectedFriendIds([])
      setGroupModalOpen(false)
      await loadRooms()
      await enterRoom(room)
    } catch { setError('단체방을 만들지 못했습니다.') }
  }

  async function enterRoom(room, addHistory = true) {
    if (!nickname.trim()) return setError('닉네임을 먼저 입력하세요.')
    try {
      await request(
        `/chat/room/${room.roomId}/members?nickname=${encodeURIComponent(nickname.trim())}`,
        { method: 'POST' },
      )
    } catch (requestError) {
      setError(requestError.message.includes('409') ? '정원이 가득 찬 채팅방입니다.' : '채팅방에 입장하지 못했습니다.')
      return
    }
    setError('')
    try {
      await request(
        `/notifications/rooms/${room.roomId}/read?recipient=${encodeURIComponent(nickname.trim())}`,
        { method: 'PATCH' },
      )
      setNotifications((items) => items.map((item) =>
        item.roomId === room.roomId ? { ...item, read: true } : item,
      ))
    } catch {
      // 채팅방 입장은 계속 진행하고 알림 읽음 처리는 다음 조회에서 재시도합니다.
    }
    setCurrentRoom(room)
    setMessages(await request(`/chat/room/${room.roomId}/messages?nickname=${encodeURIComponent(nickname.trim())}`))
    if (addHistory) {
      window.history.pushState({ view: 'room', roomId: room.roomId }, '')
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(`${apiBase}/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/room/${room.roomId}`, ({ body }) => {
          const incoming = JSON.parse(body)
          setMessages((items) => [...items, incoming])
          if (incoming.type !== 'CHAT') loadMembers(room.roomId)
        })
        client.subscribe(`/topic/room/${room.roomId}/online-status`, ({ body }) => setOnlineMembers(JSON.parse(body)))
        client.publish({
          destination: '/app/chat.sendMessage',
          body: JSON.stringify({ roomId: room.roomId, sender: nickname.trim(), type: 'JOIN', content: `${nickname.trim()}님이 입장했습니다.` }),
        })
        loadMembers(room.roomId)
      },
      onStompError: () => setError('채팅 서버 연결에 실패했습니다.'),
    })
    await clientRef.current?.deactivate()
    clientRef.current = client
    client.activate()
  }

  function sendMessage(event) {
    event.preventDefault()
    if (!message.trim() || !clientRef.current?.connected) return
    clientRef.current.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify({ roomId: currentRoom.roomId, sender: nickname.trim(), content: message.trim(), type: 'CHAT' }),
    })
    setMessage('')
  }

  async function attachFile(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !clientRef.current?.connected) return
    if (file.size > 20 * 1024 * 1024) return setError('첨부파일은 20MB까지 보낼 수 있습니다.')
    setUploadingAttachment(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const uploaded = await request('/chat/attachments', { method: 'POST', body: formData })
      clientRef.current.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify({
          roomId: currentRoom.roomId,
          sender: nickname.trim(),
          content: file.type.startsWith('image/') ? '사진' : `파일: ${uploaded.name}`,
          type: 'CHAT',
          attachmentUrl: uploaded.url,
          attachmentName: uploaded.name,
          attachmentType: uploaded.type,
        }),
      })
      setError('')
    } catch { setError('파일을 첨부하지 못했습니다.') }
    finally { setUploadingAttachment(false) }
  }

  async function toggleReaction(messageId, emoji) {
    try {
      await request(`/chat/messages/${messageId}/reactions?emoji=${encodeURIComponent(emoji)}`, { method: 'POST' })
      const list = await request(`/chat/room/${currentRoom.roomId}/reactions`)
      setReactions(Object.fromEntries(list.map((item) => [item.messageId, item])))
      setReactionContextMenu(null)
    } catch { setError('공감을 표시하지 못했습니다.') }
  }

  function openReactionContextMenu(event, message) {
    if (!message.id) return
    event.preventDefault()
    setReactionContextMenu({
      messageId: message.id,
      x: Math.min(event.clientX, window.innerWidth - 250),
      y: Math.min(event.clientY, window.innerHeight - 65),
    })
  }

  async function exitRoom() {
    await clientRef.current?.deactivate()
    clientRef.current = null
    setCurrentRoom(null)
    setMessages([])
    loadRooms()
  }

  useEffect(() => {
    async function handleBack(event) {
      const view = event.state?.view
      if (view === 'lobby') {
        await exitRoom()
        return
      }
      if (view === 'login') {
        await clientRef.current?.deactivate()
        clientRef.current = null
        setCurrentRoom(null)
        setMessages([])
        setLoggedIn(false)
        setNickname('')
        setNotifications([])
        return
      }
      if (view === 'room' && !currentRoom) {
        const room = rooms.find((item) => item.roomId === event.state.roomId)
        if (room) await enterRoom(room, false)
      }
    }

    window.addEventListener('popstate', handleBack)
    return () => window.removeEventListener('popstate', handleBack)
  }, [currentRoom, rooms, nickname])

  function openRoomContextMenu(event, room) {
    event.preventDefault()
    setFriendContextMenu(null)
    setRoomContextMenu({
      room,
      x: Math.min(event.clientX, window.innerWidth - 170),
      y: Math.min(event.clientY, window.innerHeight - 60),
    })
  }

  async function leaveRoomPermanently(room) {
    setRoomContextMenu(null)
    const confirmed = window.confirm('이 채팅방에서 영구히 나갈까요? 참여자 목록에서도 삭제됩니다.')
    if (!confirmed) return

    try {
      await request(
        `/chat/room/${room.roomId}/members/${encodeURIComponent(nickname)}`,
        { method: 'DELETE' },
      )
      setNotifications((items) => items.filter((item) => item.roomId !== room.roomId))
      if (currentRoom?.roomId === room.roomId) {
        await clientRef.current?.deactivate()
        clientRef.current = null
        setCurrentRoom(null)
        setMessages([])
        setMembers([])
        setOnlineMembers([])
      }
      await loadRooms()
    } catch {
      setError('채팅방에서 나가지 못했습니다. 잠시 후 다시 시도하세요.')
    }
  }

  function senderProfileImage(sender) {
    if (sender === nickname) return currentUser?.profileImageUrl || ''
    return friends.find((friend) => friend.nickname === sender)?.profileImageUrl || ''
  }

  if (authLoading) return (
    <main className="login-shell"><section className="login-card"><p>로그인 정보를 확인하고 있습니다.</p></section></main>
  )

  if (!loggedIn) return (
    <main className="login-shell">
      <section className="login-card">
        <p className="eyebrow">CHATTING ALARM</p>
        <h1>소셜 로그인</h1>
        <p>소셜 계정으로 로그인하면 중복되지 않는 사용자로 채팅할 수 있습니다.</p>
        {error && <div className="error">{error}</div>}
        <div className="social-login-list">
          <button className="social-button kakao" type="button" onClick={() => socialLogin('kakao')}>카카오로 로그인</button>
          <button className="social-button naver" type="button" onClick={() => socialLogin('naver')}>네이버로 로그인</button>
          <button className="social-button google" type="button" onClick={() => socialLogin('google')}>Google로 로그인</button>
        </div>
        <div className="login-divider"><span>OAuth 키 발급 전 테스트</span></div>
        <form className="dev-login-form" onSubmit={devLogin}>
          <input value={devNickname} onChange={(event) => setDevNickname(event.target.value)} placeholder="테스트용 닉네임" />
          <button type="submit">개발용 임시 로그인</button>
        </form>
      </section>
    </main>
  )

  return (
    <main className="messenger-shell">
      <aside className="profile-rail">
        <div className="brand"><span className="brand-mark">C</span><strong>Connect</strong></div>
        <div className="profile-block">
          <button className="avatar profile-image-button" type="button" onClick={openProfileModal} title="프로필 편집">
            {currentUser?.profileImageUrl ? <img src={profileImageSrc(currentUser.profileImageUrl)} alt="내 프로필" /> : nickname.slice(0, 1).toUpperCase()}
          </button>
          <div><strong>{nickname}</strong><small><i /> 온라인</small><small className="friend-code">내 코드 {currentUser?.friendCode || '-'}</small></div>
        </div>
        <nav className="profile-nav">
          <button className="rail-button" type="button" onClick={() => setSettingsOpen(true)}>⚙ 설정</button>
        </nav>
        <button className="rail-logout" type="button" onClick={logout}>로그아웃</button>
      </aside>

      <aside className="room-rail">
        <header><div><p className="eyebrow">MY CHATS</p><h2>대화</h2></div><div className="chat-header-tools"><span>{rooms.length}</span><button type="button" onClick={() => { setGroupName(''); setSelectedFriendIds([]); setError(''); setGroupModalOpen(true) }} title="단체 대화 만들기">＋</button></div></header>
        <div className="room-menu">
          {rooms.map((room) => {
            const unreadCount = notifications.filter((item) => item.roomId === room.roomId && !item.read).length
            return (
              <button className={`room-menu-item ${currentRoom?.roomId === room.roomId ? 'active' : ''}`} key={room.roomId} onClick={() => enterRoom(room)} onContextMenu={(event) => openRoomContextMenu(event, room)}>
                <span className={`room-avatar ${room.roomType === 'DIRECT' ? 'direct' : 'group'}`}>{room.roomType === 'DIRECT' && room.profileImageUrl ? <img src={profileImageSrc(room.profileImageUrl)} alt="" /> : (room.name?.trim().slice(0, 1) || '?')}</span>
                <span><strong>{room.roomType === 'DIRECT' ? room.name : room.name}</strong><small>{room.roomType === 'DIRECT' ? '1:1 대화' : `${room.currentParticipants}명 단체 대화`}</small></span>
                {unreadCount > 0 && <b className="room-unread-badge">{unreadCount > 99 ? '99+' : unreadCount}</b>}
              </button>
            )
          })}
          {!rooms.length && <p className="empty compact">오른쪽 친구 목록에서 대화를 시작하세요.</p>}
        </div>
      </aside>

      <section className="conversation">
        {currentRoom ? <>
          <header className="conversation-header">
            <div><h2>{currentRoom.name}</h2><p>{onlineMembers.length}명 접속 중</p></div>
          </header>
          <div className="member-strip"><span>참여자 · {members.join(', ') || '-'}</span><span className="online">● 온라인 · {onlineMembers.join(', ') || '-'}</span></div>
          <section className="messages" ref={messagesRef}>
            {messages.filter((item) => item.type !== 'JOIN').map((item, index) => item.type === 'CHAT' ? (
              <article className={item.sender === nickname.trim() ? 'message mine' : 'message'} key={`${item.id ?? index}-${item.timestamp}`} onContextMenu={(event) => openReactionContextMenu(event, item)}>
                <span className="message-avatar">{senderProfileImage(item.sender) ? <img src={profileImageSrc(senderProfileImage(item.sender))} alt="" /> : item.sender?.slice(0, 1)}</span>
                <div><strong>{item.sender}</strong>{!item.attachmentUrl && <p>{item.content}</p>}
                  {item.attachmentUrl && (item.attachmentType?.startsWith('image/')
                    ? <a className="chat-image-link" href={profileImageSrc(item.attachmentUrl)} target="_blank" rel="noreferrer"><img className="chat-image" src={profileImageSrc(item.attachmentUrl)} alt={item.attachmentName || '첨부 사진'} onLoad={() => { const container = messagesRef.current; if (container) container.scrollTop = container.scrollHeight }} /></a>
                    : <a className="chat-file" href={profileImageSrc(item.attachmentUrl)} target="_blank" rel="noreferrer">📎 {item.attachmentName || '첨부파일 열기'}</a>)}
                  <div className="message-reactions">
                    {Object.entries(reactions[item.id]?.counts || {}).map(([emoji, count]) => <button className={reactions[item.id]?.mine?.includes(emoji) ? 'mine' : ''} type="button" key={emoji} onClick={() => toggleReaction(item.id, emoji)}>{emoji} {count}</button>)}
                  </div>
                  <time>{item.timestamp ? new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</time></div>
              </article>
            ) : <p className="system" key={`${item.type}-${index}`}>{item.content}</p>)}
          </section>
          <form className="composer" onSubmit={sendMessage}><button className="attach-button" type="button" title="파일 또는 사진 첨부" aria-label="파일 또는 사진 첨부" disabled={uploadingAttachment} onClick={() => attachmentInputRef.current?.click()}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21.4 11.6 12 21a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5" /></svg></button><input ref={attachmentInputRef} className="profile-image-input" type="file" onChange={attachFile} /><input value={message} onChange={(e) => setMessage(e.target.value)} placeholder={uploadingAttachment ? '파일을 업로드하고 있습니다...' : `${currentRoom.name}에 메시지 보내기`} autoFocus /><button>전송</button></form>
        </> : <div className="conversation-empty"><div className="empty-symbol">💬</div><h1>대화를 시작해 보세요</h1><p>왼쪽에서 채팅방을 선택하거나 새로운 방을 만들어보세요.</p></div>}
      </section>

      <aside className="friend-rail">
        <header><div><p className="eyebrow">CONTACTS</p><h2>친구 목록</h2></div><span className="friend-total">{friends.length}</span></header>
        <div className="my-code-card"><small>내 친구 코드</small><strong>{currentUser?.friendCode || '-'}</strong></div>
        <form className="friend-add-form" onSubmit={addFriend}>
          <input value={friendCodeInput} onChange={(event) => setFriendCodeInput(event.target.value.toUpperCase())} placeholder="친구 코드 입력" maxLength="12" />
          <button title="친구 추가">＋</button>
        </form>
        {error && <div className="sidebar-error">{error}</div>}
        <div className="friend-list right-list">
          {friends.map((friend, index) => <button className="friend-item" key={friend.id} onClick={() => startDirectChat(friend)} onContextMenu={(event) => openFriendContextMenu(event, friend)}><span className={`mini-avatar tone-${index % 4}`}>{friend.profileImageUrl ? <img src={profileImageSrc(friend.profileImageUrl)} alt="" /> : friend.nickname.slice(0, 1)}</span><span><strong>{friend.nickname}</strong><small className={friend.online ? 'friend-online' : 'friend-offline'}><i /> {friend.online ? '온라인' : '오프라인'} · 1:1 대화</small></span><b>›</b></button>)}
          {!friends.length && <p className="empty compact">친구 코드를 입력해 친구를 추가하세요.</p>}
        </div>
      </aside>

      {friendContextMenu && <div className="friend-context-menu" style={{ left: friendContextMenu.x, top: friendContextMenu.y }} onClick={(event) => event.stopPropagation()}>
        <button type="button" onClick={() => deleteFriend(friendContextMenu.friend)}>친구 삭제하기</button>
      </div>}

      {roomContextMenu && <div className="room-context-menu" style={{ left: roomContextMenu.x, top: roomContextMenu.y }} onClick={(event) => event.stopPropagation()}>
        <button type="button" onClick={() => leaveRoomPermanently(roomContextMenu.room)}>영구 나가기</button>
      </div>}

      {reactionContextMenu && <div className="reaction-context-menu" style={{ left: reactionContextMenu.x, top: reactionContextMenu.y }} onClick={(event) => event.stopPropagation()}>
        {['❤️', '👍', '😂', '😮', '😢', '🎉'].map((emoji) => <button type="button" key={emoji} onClick={() => toggleReaction(reactionContextMenu.messageId, emoji)}>{emoji}</button>)}
      </div>}

      {profileModalOpen && <div className="modal-backdrop" onMouseDown={() => setProfileModalOpen(false)}>
        <form className="profile-modal" onSubmit={saveProfile} onMouseDown={(event) => event.stopPropagation()}>
          <header><div><p className="eyebrow">MY PROFILE</p><h2>프로필 편집</h2></div><button className="modal-close" type="button" onClick={() => setProfileModalOpen(false)}>×</button></header>
          <button className="profile-preview" type="button" onClick={() => profileImageInputRef.current?.click()} title="사진 선택">
            {profilePreview ? <img src={profilePreview} alt="프로필 미리보기" /> : profileName.trim().slice(0, 1).toUpperCase()}
            <span>사진 변경</span>
          </button>
          <input ref={profileImageInputRef} className="profile-image-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={selectProfileImage} />
          <label>이름<input value={profileName} onChange={(event) => setProfileName(event.target.value)} maxLength="40" placeholder="이름을 입력하세요" /></label>
          {error && <div className="modal-error">{error}</div>}
          <button className="profile-save-button" type="submit">변경사항 저장</button>
        </form>
      </div>}

      {settingsOpen && <div className="modal-backdrop" onMouseDown={() => setSettingsOpen(false)}>
        <section className="settings-modal" onMouseDown={(event) => event.stopPropagation()}>
          <header><div><p className="eyebrow">SETTINGS</p><h2>화면 설정</h2></div><button className="modal-close" type="button" onClick={() => setSettingsOpen(false)}>×</button></header>
          <p className="settings-description">사용할 화면 모드를 선택하세요.</p>
          <div className="theme-options">
            <button className={theme === 'light' ? 'selected' : ''} type="button" onClick={() => setTheme('light')}>
              <span className="theme-preview light-preview"><i /><i /><i /></span>
              <strong>화이트 모드</strong><small>밝고 깨끗한 화면</small>
            </button>
            <button className={theme === 'dark' ? 'selected' : ''} type="button" onClick={() => setTheme('dark')}>
              <span className="theme-preview dark-preview"><i /><i /><i /></span>
              <strong>다크 모드</strong><small>눈이 편안한 어두운 화면</small>
            </button>
          </div>
          <button className="settings-done" type="button" onClick={() => setSettingsOpen(false)}>완료</button>
        </section>
      </div>}

      {groupModalOpen && <div className="modal-backdrop" onMouseDown={() => setGroupModalOpen(false)}>
        <form className="group-modal" onSubmit={createGroupChat} onMouseDown={(event) => event.stopPropagation()}>
          <header><div><p className="eyebrow">NEW GROUP CHAT</p><h2>단체 대화 만들기</h2></div><button className="modal-close" type="button" onClick={() => setGroupModalOpen(false)}>×</button></header>
          <label className="group-name-field">단체방 이름<input value={groupName} onChange={(event) => setGroupName(event.target.value)} placeholder="단체방 이름을 입력하세요" autoFocus /></label>
          <section className="invite-section">
            <strong>참여 인원 <small>{selectedFriendIds.length + 1}명</small></strong>
            <div className="invited-people">
              <div className="invited-self"><span className="mini-avatar">{nickname.slice(0, 1)}</span><span>{nickname}</span><b>나</b></div>
              {friends.filter((friend) => selectedFriendIds.includes(friend.id)).map((friend) => <button type="button" key={friend.id} onClick={() => toggleGroupFriend(friend.id)}><span className="mini-avatar">{friend.nickname.slice(0, 1)}</span><span>{friend.nickname}</span><b>×</b></button>)}
              {!selectedFriendIds.length && <p>본인 외에 초대할 친구를 아래에서 선택하세요.</p>}
            </div>
          </section>
          <section className="modal-friend-section">
            <strong>친구 목록</strong>
            <div className="modal-friend-list">
              {friends.map((friend) => {
                const selected = selectedFriendIds.includes(friend.id)
                return <div className="modal-friend-row" key={friend.id}><span className="mini-avatar">{friend.profileImageUrl ? <img src={profileImageSrc(friend.profileImageUrl)} alt="" /> : friend.nickname.slice(0, 1)}</span><span><strong>{friend.nickname}</strong><small className={friend.online ? 'friend-online' : 'friend-offline'}>{friend.online ? '온라인' : '오프라인'}</small></span><button className={selected ? 'selected' : ''} type="button" onClick={() => toggleGroupFriend(friend.id)}>{selected ? '✓' : '＋'}</button></div>
              })}
              {!friends.length && <p className="empty">초대할 수 있는 친구가 없습니다.</p>}
            </div>
          </section>
          {error && <div className="modal-error">{error}</div>}
          <button className="create-group-button" type="submit" disabled={!groupName.trim() || !selectedFriendIds.length}>단체 대화 시작</button>
        </form>
      </div>}
    </main>
  )
}
