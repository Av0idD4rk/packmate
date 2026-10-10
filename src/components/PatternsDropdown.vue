<template>
	<b-dropdown no-flip text="Patterns" block variant="dark" class="col-sm-1 mr-0 p-0">
		<li role="presentation" style="padding-left: 0.5em; padding-right: 0.5em;">
			<button role="menuitem" type="button" class="btn btn-sm btn-primary btn-block"
					@click.stop.prevent="showAddPattern">
				<i class="fas fa-plus"/>
			</button>
		</li>
		<b-dropdown-divider/>
		<b-dropdown-item-button @click.stop.prevent="resetPatternSelection">
			<strong>All streams</strong>
		</b-dropdown-item-button>
		<b-dropdown-item-button v-for="pattern in existingPatterns"
								:key="pattern.id" @click.stop.prevent="openPattern(pattern)"
								:class="{ 'ignore-pattern' : pattern.actionType === 'IGNORE' }">

			<strong v-if="pattern.enabled" :style="getPatternColor">{{ pattern.name }}</strong>
			<s v-else :style="`color: ${pattern.color};`">{{ pattern.name }}</s>:

			<code>{{ getSearchTypeValue(pattern.searchType, pattern.value) }}</code>;
			<span v-if="pattern.searchTarget && pattern.searchTarget !== 'CONTENT'">{{ getSearchTargetLabel(pattern.searchTarget) }}; </span>

      <template v-if="pattern.actionType === 'FIND'">search </template>
      <template v-else>ignore </template>

			<template v-if="pattern.directionType === 'BOTH'">anywhere </template>
			<template v-else-if="pattern.directionType === 'INPUT'">in request </template>
			<template v-else>in response </template>

			<template v-if="pattern.serviceId === null">of any service</template>
			<template v-else>of service {{ getServiceName(pattern.serviceId) }} #{{ pattern.serviceId }}</template>

			<div class="float-right" style="margin-left: 2em;">
				<button type="button" class="btn btn-outline-info btn-sm mr-1"
								@click.stop.prevent="showEditPattern(pattern)"
								title="Edit pattern">
					<i class="fas fa-edit"></i>
				</button>

				<button v-if="pattern.actionType === 'FIND'" type="button" class="btn btn-outline-warning btn-sm mr-1"
								@click.stop.prevent="showLookBack(pattern)"
								title="Apply pattern to older streams">
					<i class="fas fa-backward"></i>
				</button>

				<button v-if="pattern.enabled" type="button" class="btn btn-outline-danger btn-sm mr-1"
						@click.stop.prevent="togglePattern(pattern)"
						title="Stop matching streams with this pattern">
					<i class="fas fa-pause"></i>
				</button>
				<button v-else type="button" class="btn btn-outline-success btn-sm mr-1"
						@click.stop.prevent="togglePattern(pattern)"
						title="Start matching streams with this pattern again">
					<i class="fas fa-play"></i>
				</button>

				<button type="button" class="btn btn-outline-danger btn-sm"
								@click.stop.prevent="deletePattern(pattern)"
								title="Permanently delete this pattern">
					<i class="fas fa-trash"></i>
				</button>
			</div>
		</b-dropdown-item-button>

		<PatternModal :creating="patternModalIsCreating" :initial-pattern="patternModalEditingPattern" />
		<LookBack :pattern-id="patternIdForLookback" />
	</b-dropdown>
</template>
<!--suppress JSUnresolvedReference -->
<script>
	import PatternModal from '@/views/PatternModal.vue';
	import LookBack from '@/views/LookBack.vue';

	export default {
		name: 'PatternsDropdown',
		components: {LookBack, PatternModal, },
		data() {
			return {
				patternModalIsCreating: true,
				patternModalEditingPattern: {},
				patternIdForLookback: 0,
			};
		},
		mounted() {
			this.updatePatterns();
		},
		computed: {
			existingPatterns: function () {
				return this.$store.state.patterns.filter(p => !p.deleted)
			},
		},
		methods: {
			getSearchTargetLabel(target) {
				return {SOURCE_IP: 'source IP', DESTINATION_IP: 'destination IP', EITHER_IP: 'either IP address',}[target];
			},
			getSearchTypeValue(searchType, value) {
				if (searchType === 'REGEX') return `/${value}/`;
				else if (searchType === 'SUBSTRING') return `'${value}'`;
				else return `0x${value}`;
			},
    	getPatternColor(pattern) {
				if (pattern.actionType === 'FIND') {
					return `color: ${pattern.color};`;
				} else {
					return `color: inherit;`;
				}
    	},
			getServiceName(port) {
				return this.$store.state.services.find(o => o.port === port)?.name ?? '<Deleted service>'
			},
			showAddPattern() {
				this.patternModalIsCreating = true;
				this.patternModalEditingPattern = {};
				this.$bvModal.show('patternModal');
				console.debug('Showing patternModal (create)');
			},
			showEditPattern(pattern) {
				this.patternModalIsCreating = false;
				this.patternModalEditingPattern = {};
				this.$nextTick(() => {
					this.patternModalEditingPattern = pattern;
					this.$bvModal.show('patternModal');
					console.debug('Showing patternModal (edit)');
				});
			},
			showLookBack(pattern) {
				this.patternIdForLookback = pattern.id;
				this.$bvModal.show('lookBackModal');
			},
			updatePatterns() {
				this.$http.get('pattern/')
					.then(r => this.$store.commit('setPatterns', r.data))
					.catch(e => {
						this.$bvToast.toast(`Failed to load patterns: ${e}`, {
							title: 'Error',
							variant: 'danger',
						});
						console.error('Failed to load patterns:', e);
					});
			},
			openPattern(pattern) {
				if (pattern.actionType === 'IGNORE') {
					return;
				}

				console.debug('Opening pattern w/ id', pattern.id);
				this.$router.push({
					name: 'stream',
					params: this.$route.params,
					query: {pattern: pattern.id,},
				}, () => {});
			},
			resetPatternSelection() {
				console.debug('Resetting pattern selection');
				this.$router.push({
					name: 'stream',
					params: this.$route.params,
				}, () => {});
			},
			togglePattern(pattern) {
				const enabled = !pattern.enabled;
				console.debug('Toggling pattern', pattern);
				this.$http.post(`pattern/${pattern.id}/enable`, null, {
					params: {
						enabled,
					},
				})
					.then(response => {
						const data = response.data;
						console.debug('Done toggling pattern', data);
						this.$emit('patternAddComplete');
					}).catch(e => {
					this.$bvToast.toast(`Failed to toggle pattern: ${e}`, {
						title: 'Error',
						variant: 'danger',
					});
					console.error('Failed to toggle pattern', e);
				});
			},
			deletePattern(pattern) {
				console.debug('Deleting pattern', pattern);

				this.$http.delete(`pattern/${pattern.id}`, null)
						.then(() => {
							console.debug('Done deleting pattern');
							this.$emit('patternAddComplete');
						}).catch(e => {
							this.$bvToast.toast(`Failed to delete pattern: ${e}`, {
								title: 'Error',
								variant: 'danger',
							});
							console.error('Failed to delete pattern', e);
						});
			},
		},
	};
</script>
<style scoped>
	code {
		font-family: "Ubuntu Mono", "Lucida Console", monospace;
		font-size: 100%;
	}
</style>
<style>
	.ignore-pattern button {
		cursor: default !important;
	}
</style>
