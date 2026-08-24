/**
 * 德州扑克游戏逻辑
 */

class PokerGame {
    constructor() {
        this.ws = null;
        this.playerId = null;
        this.username = '';
        this.avatar = '';
        this.seatIndex = -1;
        this.gameState = null;
        this.isMyTurn = false;
        
        this.init();
    }
    
    init() {
        this.bindEvents();
    }
    
    bindEvents() {
        // 头像选择
        document.querySelectorAll('.avatar-option').forEach(option => {
            option.addEventListener('click', (e) => {
                document.querySelectorAll('.avatar-option').forEach(o => o.classList.remove('selected'));
                e.target.classList.add('selected');
                this.avatar = e.target.dataset.avatar;
            });
        });
        
        // 加入游戏按钮
        document.getElementById('join-btn').addEventListener('click', () => {
            this.joinGame();
        });
        
        // 旁观按钮
        document.getElementById('spectate-btn').addEventListener('click', () => {
            this.spectate();
        });
        
        // 开始游戏按钮
        document.getElementById('start-btn').addEventListener('click', () => {
            this.startGame();
        });
        
        // 离开按钮
        document.getElementById('leave-btn').addEventListener('click', () => {
            this.leaveGame();
        });
        
        // 操作按钮
        document.getElementById('btn-fold').addEventListener('click', () => {
            this.playerAction('FOLD');
        });
        
        document.getElementById('btn-check').addEventListener('click', () => {
            this.playerAction('CHECK');
        });
        
        document.getElementById('btn-call').addEventListener('click', () => {
            this.playerAction('CALL');
        });
        
        document.getElementById('btn-raise').addEventListener('click', () => {
            const raiseAmount = parseInt(document.getElementById('raise-slider').value);
            this.playerAction('RAISE', raiseAmount);
        });
        
        document.getElementById('btn-allin').addEventListener('click', () => {
            this.playerAction('ALL_IN');
        });
        
        // 加注滑块
        document.getElementById('raise-slider').addEventListener('input', (e) => {
            document.getElementById('raise-amount').textContent = e.target.value;
        });
        
        // 用户名输入
        document.getElementById('username').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.joinGame();
            }
        });
    }
    
    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/poker`;
        
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            console.log('WebSocket 连接成功');
            this.showMessage('连接成功');
        };
        
        this.ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this.handleMessage(data);
        };
        
        this.ws.onclose = () => {
            console.log('WebSocket 连接关闭');
            this.showMessage('连接断开');
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket 错误:', error);
        };
    }
    
    sendMessage(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        }
    }
    
    handleMessage(data) {
        console.log('收到消息:', data);
        
        switch (data.type) {
            case 'welcome':
                this.playerId = data.playerId;
                this.username = data.username;
                this.seatIndex = data.seatIndex;
                this.showGameScreen();
                this.connect(); // 建立游戏 WebSocket 连接
                break;
                
            case 'gameState':
                this.updateGameState(data);
                break;
                
            case 'spectate':
                this.updateGameState(data);
                this.showMessage('进入旁观模式');
                break;
                
            case 'error':
                this.showMessage(data.message);
                break;
                
            default:
                // 可能是游戏状态更新
                if (data.phase) {
                    this.updateGameState(data);
                }
        }
    }
    
    joinGame() {
        const usernameInput = document.getElementById('username');
        this.username = usernameInput.value.trim() || 'Player_' + Math.floor(Math.random() * 10000);
        
        if (!this.avatar) {
            this.avatar = '🐶';
        }
        
        // 发送加入请求（这里简化处理，实际应通过 WebSocket）
        this.playerId = 'player_' + Date.now();
        this.showGameScreen();
        this.connect();
        
        // 模拟加入
        setTimeout(() => {
            this.sendMessage({
                action: 'join',
                username: this.username,
                avatar: this.avatar
            });
        }, 500);
    }
    
    spectate() {
        this.showGameScreen();
        this.connect();
        
        setTimeout(() => {
            this.sendMessage({
                action: 'spectate'
            });
        }, 500);
    }
    
    startGame() {
        this.sendMessage({
            action: 'start'
        });
    }
    
    leaveGame() {
        this.sendMessage({
            action: 'leave',
            playerId: this.playerId
        });
        
        this.showLoginScreen();
    }
    
    playerAction(action, raiseAmount = 0) {
        if (!this.isMyTurn || !this.playerId) {
            return;
        }
        
        this.sendMessage({
            action: 'action',
            playerId: this.playerId,
            actionType: action,
            raiseAmount: raiseAmount
        });
        
        this.isMyTurn = false;
        this.updateActionButtons();
    }
    
    updateGameState(state) {
        this.gameState = state;
        
        // 更新阶段显示
        document.getElementById('phase-display').textContent = state.phase || '等待开始';
        document.getElementById('pot-display').textContent = state.pot || 0;
        document.getElementById('table-pot').textContent = state.pot || 0;
        
        // 更新公共牌
        this.updateCommunityCards(state.communityCards || []);
        
        // 更新玩家信息
        if (state.players) {
            this.updatePlayers(state.players);
            
            // 检查是否是当前玩家的回合
            const currentPlayerIndex = state.currentPlayerIndex;
            const mySeatIndex = this.seatIndex;
            this.isMyTurn = (currentPlayerIndex === mySeatIndex);
            
            // 更新操作按钮
            this.updateActionButtons();
        }
        
        // 更新玩家筹码显示
        if (this.playerId && state.players) {
            const myPlayer = state.players.find(p => p.id === this.playerId);
            if (myPlayer) {
                document.getElementById('player-chips').textContent = myPlayer.chips;
                document.getElementById('player-name').textContent = myPlayer.username;
            }
        }
    }
    
    updateCommunityCards(cards) {
        const container = document.getElementById('community-cards');
        container.innerHTML = '';
        
        for (let i = 0; i < 5; i++) {
            if (i < cards.length) {
                const cardEl = this.createCardElement(cards[i]);
                container.appendChild(cardEl);
            } else {
                const placeholder = document.createElement('div');
                placeholder.className = 'card-placeholder';
                container.appendChild(placeholder);
            }
        }
    }
    
    updatePlayers(players) {
        // 清空所有座位
        document.querySelectorAll('.seat').forEach(seat => {
            seat.classList.remove('active');
            const playerCard = seat.querySelector('.player-card');
            playerCard.querySelector('.avatar').textContent = '🎮';
            playerCard.querySelector('.player-name').textContent = '空位';
            playerCard.querySelector('.chips').textContent = '0';
            playerCard.querySelector('.cards').innerHTML = `
                <div class="card back"></div>
                <div class="card back"></div>
            `;
            playerCard.querySelector('.dealer-button').style.display = 'none';
        });
        
        // 更新玩家座位
        players.forEach(player => {
            const seat = document.querySelector(`.seat-${player.seatIndex}`);
            if (seat) {
                seat.classList.add('active');
                const playerCard = seat.querySelector('.player-card');
                
                playerCard.querySelector('.avatar').textContent = player.avatar || '🎮';
                
                // 显示 AI 风格标识
                let playerName = player.username;
                if (player.isAI && player.aiStyle) {
                    playerName += ' <span class="ai-badge">' + this.getAIStyleName(player.aiStyle) + '</span>';
                }
                playerCard.querySelector('.player-name').innerHTML = playerName;
                
                playerCard.querySelector('.chips').textContent = player.chips;
                
                // 更新手牌
                const cardsContainer = playerCard.querySelector('.cards');
                cardsContainer.innerHTML = '';
                
                if (player.handCards && player.handCards.length > 0) {
                    // 如果是自己的牌或已摊牌，显示牌面
                    const showCards = player.seatIndex === this.seatIndex || this.gameState.phase === '摊牌';
                    
                    player.handCards.forEach(cardName => {
                        if (showCards) {
                            cardsContainer.appendChild(this.createCardElement(cardName));
                        } else {
                            const backCard = document.createElement('div');
                            backCard.className = 'card back';
                            cardsContainer.appendChild(backCard);
                        }
                    });
                } else {
                    cardsContainer.innerHTML = `
                        <div class="card back"></div>
                        <div class="card back"></div>
                    `;
                }
                
                // 庄家按钮
                const dealerButton = playerCard.querySelector('.dealer-button');
                dealerButton.style.display = player.isDealer ? 'flex' : 'none';
                
                // 盲注标识
                if (player.isSmallBlind) {
                    playerCard.querySelector('.player-name').textContent += ' (SB)';
                }
                if (player.isBigBlind) {
                    playerCard.querySelector('.player-name').textContent += ' (BB)';
                }
                
                // 弃牌状态
                if (player.folded) {
                    seat.style.opacity = '0.5';
                } else {
                    seat.style.opacity = '1';
                }
            }
        });
        
        // 高亮当前玩家
        if (this.gameState.currentPlayerIndex !== undefined) {
            const currentSeat = document.querySelector(`.seat-${this.gameState.currentPlayerIndex}`);
            if (currentSeat) {
                currentSeat.classList.add('thinking');
            }
        }
    }
    
    /**
     * 获取 AI 风格名称
     */
    getAIStyleName(style) {
        const styles = {
            'AGGRESSIVE': '🦈激进',
            'CONSERVATIVE': '🦉保守',
            'RANDOM': '🎭随机',
            'CALCULATOR': '🤖计算',
            'CALLER': '🐑跟随'
        };
        return styles[style] || 'AI';
    }
    
    createCardElement(cardName) {
        const card = document.createElement('div');
        card.className = 'card';
        
        // 解析牌名，如 "A♠", "10♥"
        const suit = cardName.slice(-1);
        const rank = cardName.slice(0, -1);
        
        card.textContent = rank + suit;
        
        if (suit === '♥' || suit === '♦') {
            card.classList.add('red');
        } else {
            card.classList.add('black');
        }
        
        return card;
    }
    
    updateActionButtons() {
        const checkBtn = document.getElementById('btn-check');
        const callBtn = document.getElementById('btn-call');
        const raiseBtn = document.getElementById('btn-raise');
        const allinBtn = document.getElementById('btn-allin');
        const foldBtn = document.getElementById('btn-fold');
        
        const disabled = !this.isMyTurn;
        
        checkBtn.disabled = disabled;
        callBtn.disabled = disabled;
        raiseBtn.disabled = disabled;
        allinBtn.disabled = disabled;
        foldBtn.disabled = disabled;
        
        // 根据游戏状态显示/隐藏按钮
        const currentBet = this.gameState.currentBet || 0;
        const myPlayer = this.gameState.players?.find(p => p.id === this.playerId);
        const myBet = myPlayer?.currentBet || 0;
        const toCall = currentBet - myBet;
        
        if (toCall === 0) {
            checkBtn.style.display = 'inline-block';
            callBtn.style.display = 'none';
        } else {
            checkBtn.style.display = 'none';
            callBtn.style.display = 'inline-block';
            callBtn.textContent = `跟注 ${toCall}`;
        }
    }
    
    showGameScreen() {
        document.getElementById('login-screen').classList.remove('active');
        document.getElementById('game-screen').classList.add('active');
    }
    
    showLoginScreen() {
        document.getElementById('game-screen').classList.remove('active');
        document.getElementById('login-screen').classList.add('active');
    }
    
    showMessage(text) {
        const toast = document.getElementById('message-toast');
        toast.textContent = text;
        toast.style.display = 'block';
        
        setTimeout(() => {
            toast.style.display = 'none';
        }, 2000);
    }
}

// 初始化游戏
const game = new PokerGame();
