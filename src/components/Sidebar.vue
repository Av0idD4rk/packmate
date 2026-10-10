<template>
	<nav class="col-sm-3 d-none d-sm-block sidebar"
			 :class="this.$store.state.theme === 'light' ? 'bg-light' : 'bg-dark'">
		<div class="m-2">
			<button type="button" class="btn btn-sm"
					:title="this.$store.state.pause ? 'Continue' : 'Pause new streams'"
					:class="this.$store.state.pause ? 'btn-danger' : 'btn-outline-success'"
					@click.stop.prevent="togglePause">
				<i :class="this.$store.state.pause ? 'fas fa-play' : 'fas fa-pause'"/>
			</button>
			<button type="button" class="btn btn-sm ml-1"
					:title="this.$store.state.displayFavoritesOnly ? 'Show all streams' : 'Show only favorite streams'"
					:class="this.$store.state.displayFavoritesOnly ? 'btn-danger' : 'btn-outline-danger'"
					@click.stop.prevent="toggleFavorites">
				<i class="fas fa-star"/>
			</button>
			<button type="button" class="btn btn-outline-primary btn-sm ml-1"
					:title="this.$store.state.hexdumpMode ? 'Switch to text view' : 'Switch to hexdump view'"
					@click.stop.prevent="toggleHexdump">
				<i :class="this.$store.state.hexdumpMode ? 'far fa-file-code' : 'fas fa-align-left'"/>
			</button>
			<button type="button" class="btn btn-sm btn-outline-warning ml-1" v-if="!this.$store.state.pcapStarted"
					title="Start pcap file processing"
					@click.stop.prevent="startPcap">
				<i class="fas fa-arrow-circle-down"/>
			</button>
			<button type="button" class="btn btn-sm btn-outline-info" style="float: right;"
					title="Scroll to top"
					@click.stop.prevent="scrollUp">
				<i class="fas fa-angle-double-up"/>
			</button>
		</div>
		<div class="sidebar-sticky">
			<ul class="nav flex-column">
				<SidebarStream v-for="stream in streams"
							   :key="stream.id"
							   :stream="stream"/>
				<infinite-loading @infinite="infiniteLoadingHandler" ref="infiniteLoader"/>
			</ul>
		</div>
	</nav>
</template>

<script>
import SidebarStream from './SidebarStream';

export default {
		name: 'Sidebar',
		props: ['servicePort', 'streamId',],
		data() {
			return {
				streams: [],
				navigationKeysCallback: (e) => {
					if (!e.ctrlKey) return;
					if (e.key === 'ArrowUp') {
						e.preventDefault();
						const index = this.streams.findIndex(e => e.id === this.$route.params.streamId);
						const newStream = this.streams[index - 1];
						if (!newStream) return;
						const newId = newStream.id;
						this.$router.push({
							name: 'stream',
							params: {servicePort: this.$route.params.servicePort, streamId: newId,},
							query: this.$route.query,
						});
						document.getElementById(`stream-${newId}`).scrollIntoView({
							behavior: 'smooth',
							block: 'center',
							inline: 'center',
						});
					} else if (e.key === 'ArrowDown') {
						e.preventDefault();
						const index = this.streams.findIndex(e => e.id === this.$route.params.streamId);
						const newStream = this.streams[index + 1];
						if (!newStream) return;
						const newId = newStream.id;
						this.$router.push({
							name: 'stream',
							params: {servicePort: this.$route.params.servicePort, streamId: newId,},
							query: this.$route.query,
						});
						document.getElementById(`stream-${newId}`).scrollIntoView({
							behavior: 'smooth',
							block: 'center',
							inline: 'center',
						});
					} else if (e.key === 'Home') {
						const newStream = this.streams[0];
						if (!newStream) return;
						const newId = newStream.id;
						this.$router.push({
							name: 'stream',
							params: {servicePort: this.$route.params.servicePort, streamId: newId,},
							query: this.$route.query,
						});
						document.getElementById(`stream-${newId}`).scrollIntoView({
							behavior: 'smooth',
							block: 'center',
							inline: 'center',
						});
					} else if (e.key === 'End') {
						const newStream = this.streams[this.streams.length - 1];
						if (!newStream) return;
						const newId = newStream.id;
						this.$router.push({
							name: 'stream',
							params: {servicePort: this.$route.params.servicePort, streamId: newId,},
							query: this.$route.query,
						});
						document.getElementById(`stream-${newId}`).scrollIntoView({
							behavior: 'smooth',
							block: 'center',
							inline: 'center',
						});
					}
				},
			};
		},
		watch: {
			'$route.params.servicePort': function () {
				console.debug('Port reselected');
				this.streams = [];
				this.$refs.infiniteLoader.stateChanger.reset();
			},
			'$route.query.pattern': function () {
				console.debug('Pattern selected');
				this.streams = [];
				this.$refs.infiniteLoader.stateChanger.reset();
			},
		},
		methods: {
			infiniteLoadingHandler($state) {
				const ourStreams = this.streams;
				const pageSize = this.$store.state.pageSize;

				let startsFrom;
				if (ourStreams?.length && ourStreams[ourStreams.length - 1]) {
					startsFrom = ourStreams[ourStreams.length - 1].id;
				} else {
					startsFrom = null;
				}

				this.$http.post(`/stream/${this.$route?.params?.servicePort || 'all'}`, {
					startingFrom: startsFrom,
					pageSize: pageSize,
					pattern: this.$route.query.pattern ? {id: this.$route.query.pattern,} : null,
					favorites: this.$store.state.displayFavoritesOnly,
				}).then(r => {
					const data = r.data;
					if (data?.length === 0) {
						console.log('Finished loading streams (empty page)');
						return $state.complete();
					}

					if (data[0]?.id === this.streams[0]?.id) {
						console.log('Finished loading streams (overlap detected)');
						return $state.complete();
					}

					this.streams.push(...data);

					if (data.length < pageSize) {
						// this was the last page
						console.log('Finished loading streams (last page was not full)');
						$state.complete();
					} else {
						console.log('Loaded another page of streams');
						$state.loaded()
					}
				}).catch(e => {
					this.$bvToast.toast(`Failed to load portion of streams: ${e}`, {
						title: 'Error',
						variant: 'danger',
					});
					console.error('Failed to load portion of streams:', e);
					return $state.error();
				});
			},
			toggleFavorites() {
				this.$store.commit('toggleDisplayFavoritesOnly');
				this.streams = [];
				this.$refs.infiniteLoader.stateChanger.reset();
			},
			togglePause() {
				this.$store.commit('togglePause');
			},
			toggleHexdump() {
				this.$store.commit('toggleHexdumpMode');
			},
			startPcap() {
				this.$http.post('pcap/start')
					.then(() => {
						this.$store.commit('startPcap');
					}).catch(e => {
						this.$bvToast.toast(`Failed to start pcap: ${e}`, {
							title: 'Error',
							variant: 'danger',
						});
						console.error('Failed to start pcap', e);
					});
			},
			addStreamFromWs(stream) {
				const currentPort = parseInt(this.$route?.params?.servicePort, 10);
				if (currentPort && currentPort !== stream.service) return;
				if (this.$store.state.displayFavoritesOnly || this.$store.state.pause) return;
				if (this.$route.query.pattern && !(stream.foundPatternsIds || []).includes(Number(this.$route.query.pattern))) {
					return;
				}

				this.streams.unshift(stream);
			},
			scrollUp() {
				const newStream = this.streams[0];
				if (!newStream) return;
				const newId = newStream.id;
				this.$router.push({
					name: 'stream',
					params: {servicePort: this.$route.params.servicePort, streamId: newId,},
					query: this.$route.query,
				});
				document.getElementById(`stream-${newId}`).scrollIntoView({
					behavior: 'smooth',
					block: 'center',
					inline: 'center',
				});
			},
		},
		mounted() {
			document.addEventListener('keydown', this.navigationKeysCallback);

			this.$http.get('pcap/started')
				.then(r => this.$store.state.pcapStarted = r.data)
				.catch(e => {
					console.error('Failed to get pcap status, defaulting to true:', e);
					this.$store.state.pcapStarted = true;
				});
		},
		beforeDestroy() {
			document.removeEventListener('keydown', this.navigationKeysCallback);
		},
		components: {
			SidebarStream,
		},
	};
</script>

<style scoped>
	.sidebar {
		position: fixed;
		top: 0;
		bottom: 0;
		left: 0;
		z-index: 100; /* Behind the navbar */
		padding: 40px 0 0; /* Height of navbar */
		box-shadow: inset -1px 0 0 rgba(0, 0, 0, .1);
	}

	.sidebar-sticky {
		position: relative;
		top: 0;
		height: calc(100vh - 85px);
		/*padding-top: .5rem;*/
		overflow-x: hidden;
		overflow-y: auto; /* Scrollable contents if viewport is shorter than content. */
	}

	@supports (position: sticky) {
		.sidebar-sticky {
			position: sticky;
		}
	}

</style>
