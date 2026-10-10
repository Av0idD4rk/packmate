<template>
	<div :class="{'packet-incoming': packet.incoming, 'packet-outgoing': !packet.incoming}">
		<div>#{{ packet.id }} at {{ dateToText(packet.timestamp) }}
			<template v-if="offset !== null"> (+{{ offset }} ms)</template>
			{{ printPacketFlags(packet) }}
			<button @click.prevent="copyRaw" class="btn btn-link">Copy HEX</button>
			<button @click.prevent="copyText" class="btn btn-link">Copy text</button>
			<button @click.prevent="copyPythonBytes" class="btn btn-link">Copy as Python bytes</button>
		</div>
		<div v-if="packet.sourceIp || packet.destinationIp" class="packet-addresses">
			<span title="Source IP" :style="ipHighlightStyle('SOURCE_IP')">{{ packet.sourceIp || '?' }}</span>
			&rarr;
			<span title="Destination IP" :style="ipHighlightStyle('DESTINATION_IP')">{{ packet.destinationIp || '?' }}</span>
		</div>
		<p v-if="!this.$store.state.hexdumpMode"
			 class="pt-2 pb-2 mb-3"
			 v-html="stringdata"/>
		<p v-else
			 class="pt-2 pb-2 mb-3"
			 v-html="hexdata"/>
	</div>
</template>

<!--suppress JSUnresolvedVariable, JSDeprecatedSymbols -->
<script>
	export default {
		name: 'Packet',
		props: {
			packet: {
				id: Number(),
				matches: Array(),
				timestamp: Number(),
				sourceIp: String(),
				destinationIp: String(),
				incoming: Boolean(),
				ungzipped: Boolean(),
				webSocketParsed: Boolean(),
				tlsDecrypted: Boolean(),
				content: String(),
			},
			offset: Number(),
		},
		computed: {
			hexdata() {
				const dataString = this.atou(this.packet.content);
				const dump = this.hexdump(dataString, this.$store.state.hexdumpBlockSize, this.$store.state.hexdumpLineNumberBase);
				return this.escapeHtml(dump)
					.split('\n')
					.join('<br>'); // Replace all \n to <br>
			},
			stringdata() {
				const dataString = this.atou(this.packet.content);
				const dump = this.highlightPatterns(dataString);
				return this.escapeHtml(dump)
					.split('\n')
					.join('<br>');
			},
		},
		methods: {
			ipHighlightStyle(target) {
				const match = (this.packet.matches || []).find(match => match.searchTarget === target &&
					this.$store.state.patterns.some(pattern => pattern.id === match.patternId && !pattern.deleted));
				if (!match) return {};
				const pattern = this.$store.state.patterns.find(pattern => pattern.id === match.patternId);
				return {backgroundColor: pattern.color,};
			},
			atou(b64) {
				const text = atob(b64);
				const length = text.length;
				const bytes = new Uint8Array(length);
				for (let i = 0; i < length; i++) {
					bytes[i] = text.charCodeAt(i);
				}
				const decoder = new TextDecoder();
				return decoder.decode(bytes);
			},
			printPacketFlags(packet) {
				let flags = [];

				if (packet.ungzipped) {
					flags.push('GZIP');
				}

				if (packet.webSocketParsed) {
					flags.push('WS');
				}

				if (packet.tlsDecrypted) {
					flags.push('TLS');
				}

				return flags.join(' ');
			},
			hexdump(buffer, blockSize, lineNumberBase) {
				blockSize = parseInt(blockSize, 10) || 16;
				lineNumberBase = parseInt(lineNumberBase, 10) || 10;
				let lines = [];
				const hex = '0123456789ABCDEF';
				for (let b = 0; b < buffer.length; b += blockSize) {
					const block = buffer.slice(b, Math.min(b + blockSize, buffer.length));
					const addr = ('0000000000' + b.toString(lineNumberBase)).slice(-10);
					let codes = block.split('').map(ch => {
						const code = ch.charCodeAt(0);
						return ' ' + hex[(0xF0 & code) >> 4] + hex[0x0F & code];
					}).join('');
					codes += ' ..'.repeat(blockSize - block.length);
					// eslint-disable-next-line no-control-regex
					let chars = block.replace(/[\x00-\x1F]/g, '.');
					chars += ' '.repeat(blockSize - block.length);
					lines.push(addr + ':' + codes + ' |' + chars + '|');
				}
				return lines.join('\n');
			},
			dateToText(unixTimestamp) {
				return new Date(unixTimestamp).toLocaleDateString('ru-RU', {
					month: '2-digit',
					day: '2-digit',
					hour: '2-digit',
					minute: '2-digit',
					second: '2-digit',
				});
			},
			escapeHtml(in_) {
				return in_.replace(/(<span style="background-color: #(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})">|<\/span>)|[&<>"'/]/g, ($0, $1) => {
					const entityMap = {
						'&': '&amp;',
						'<': '&lt;',
						'>': '&gt;',
						'"': '&quot;',
						'\'': '&#39;',
						'/': '&#x2F;',
					};

					return $1 ? $1 : entityMap[$0];
				});
			},
			highlightPatterns(raw) {
				const patterns = this.$store.state.patterns.reduce((obj, item) => {
					obj[item.id] = item;
					return obj;
				}, {}); // Array to object
				let offset = 0;
				this.packet.matches
					.filter(match => !match.searchTarget || match.searchTarget === 'CONTENT')
					.filter(match => !patterns[match.patternId].deleted)
					.sort((a, b) => a.startPosition - b.startPosition)
					.forEach(match => {
						const pattern = patterns[match.patternId];
						if (!pattern) {
							console.info(`Pattern #${match.patternId} does not exist`);
							return;
						}
						const firstTag = `<span style="background-color: ${pattern.color}">`;
						const secondTag = '</span>';

						const positionStart = match.startPosition + offset;
						raw = raw.substring(0, positionStart) + firstTag + raw.substring(positionStart);
						offset += firstTag.length;

						const positionEnd = match.endPosition + offset + 1;
						raw = raw.substring(0, positionEnd) + secondTag + raw.substring(positionEnd);
						offset += secondTag.length;
					});
				return raw;
			},

			copyPythonBytes() {
				const data = 'b\'' + atob(this.packet.content)
					.split('')
					.map((aChar) => {
						return '\\x' + ('0' + aChar.charCodeAt(0).toString(16)).slice(-2);
					})
					.join('') + '\'';
				this.copyContent(data);
			},
			copyRaw() {
				this.copyContent(Buffer.from(this.packet.content, 'base64').toString('hex'));
			},
			copyText() {
				this.copyContent(atob(this.packet.content));
			},
			copyContent(data) {
				const tempEl = document.createElement('textarea');
				tempEl.value = data; // Chrome
				tempEl.textContent = data; // Firefox
				document.body.appendChild(tempEl);
				tempEl.select();
				const result = document.execCommand('copy');
				document.body.removeChild(tempEl);
				console.debug('Copy result is', result);
			},
		},
	};
</script>

<style scoped>
	.packet-addresses {
		font-family: "Ubuntu Mono", "Lucida Console", monospace;
		overflow-wrap: anywhere;
	}

	.packet-outgoing {
		background: var(--response-packet-color);
		box-shadow: 0 0 5px 5px var(--response-packet-color);
	}

	.packet-incoming {
		background: var(--request-packet-color);
		box-shadow: 0 0 5px 5px var(--request-packet-color);
	}

	p {
		font-family: "Ubuntu Mono", "Lucida Console", monospace;
		font-size: 100%;
		margin-bottom: 10px;
		padding-bottom: 5px;
		word-break: break-word;
	}

	button {
		padding: 0;
		top: -0.1em;
		position: relative;
		margin-left: 5px;
	}
</style>
