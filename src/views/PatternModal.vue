<template>
	<b-modal @ok="addPattern" @cancel="reset" id="patternModal"
			 :title="creating ? 'Creating pattern' : 'Editing pattern'"
			 cancel-title="Cancel"
			 centered scrollable>
		<form ref="addPatternForm">
			<b-form-group
					label-cols-sm="4"
					label-cols-lg="3"
					label="Name"
					description="It is displayed in the list and highlights matching content or IP addresses"
					label-for="pattern-name">
				<b-form-input @keydown.native.enter="addPattern" id="pattern-name" required v-model="newPattern.name"/>
			</b-form-group>

			<b-form-group
					v-if="creating"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Pattern"
					description="Substring, RegEx or bytes"
					label-for="pattern-value">
				<b-form-input @keydown.native.enter="addPattern" @keydown="validateKey"
							  id="pattern-value" required v-model="newPattern.value"
							  :placeholder="getPlaceholder()"/>
			</b-form-group>

      <b-form-group
					v-if="creating"
          label-cols-sm="4"
          label-cols-lg="3"
          label="Search action"
          description="What to do with matching streams"
          label-for="pattern-actionType">
        <b-form-select id="pattern-actionType" required v-model="newPattern.actionType">
          <option value="FIND" selected>Highlight found pattern</option>
          <option value="IGNORE">Ignore matching streams</option>
        </b-form-select>
      </b-form-group>

			<b-form-group v-if="newPattern.actionType === 'FIND'"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Color"
					description="The highlight color of the pattern in the packets and streams"
					label-for="pattern-color">
				<b-form-input id="pattern-color" required type="color" v-model="newPattern.color"/>
			</b-form-group>

			<b-form-group
					v-if="creating"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Search in"
					description="IP addresses use regular expressions. Use ^ and $ to match the entire address."
					label-for="pattern-searchTarget">
				<b-form-select id="pattern-searchTarget" required v-model="newPattern.searchTarget" @change="onSearchTargetChange">
					<option value="CONTENT">Packet content</option>
					<option value="SOURCE_IP">Source IP</option>
					<option value="DESTINATION_IP">Destination IP</option>
					<option value="EITHER_IP">Either IP address</option>
				</b-form-select>
			</b-form-group>

			<b-form-group
					v-if="creating"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Search method"
					description="The way to search for patterns in packets"
					label-for="pattern-searchType">
				<b-form-select id="pattern-searchType" required v-model="newPattern.searchType" :disabled="newPattern.searchTarget !== 'CONTENT'">
					<option value="REGEX" selected>Regular expression</option>
					<option value="SUBSTRING">Substring</option>
					<option value="SUBBYTES">Bytes</option>
				</b-form-select>
			</b-form-group>

			<b-form-group
					v-if="creating"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Search type"
					description="In which packets to search for a pattern"
					label-for="pattern-type">
				<b-form-select id="pattern-type" required v-model="newPattern.directionType">
					<option value="INPUT">Request</option>
					<option value="OUTPUT">Response</option>
					<option value="BOTH" selected>Anywhere</option>
				</b-form-select>
			</b-form-group>

			<b-form-group
					v-if="creating"
					label-cols-sm="4"
					label-cols-lg="3"
					label="Service"
					description="Apply this pattern only to the specific service"
					label-for="pattern-service">
				<b-form-select id="pattern-service" :options="serviceOptions"
											 v-model="newPattern.serviceId"></b-form-select>
			</b-form-group>
		</form>
	</b-modal>
</template>

<script>

const hexRegex = /[0-9A-Fa-f ]/;
const defaultPattern = {
	name: '',
	value: '',
	color: '#FF7474',
	searchType: 'SUBSTRING',
	searchTarget: 'CONTENT',
	directionType: 'BOTH',
	actionType: 'FIND',
	serviceId: null,
};

	export default {
		name: 'PatternModal',
		props: {
			creating: Boolean,

			initialPattern: {
				name: String,
				value: String,
				color: String,
				searchType: String,
				searchTarget: String,
				directionType: String,
				actionType: String,
				serviceId: Number,
			},
		},
		data() {
			return {
				newPattern: {},
			};
		},
		watch: {
			initialPattern() {
				console.debug('initialService changed, reassigning...', this.initialPattern);
				this.newPattern = {...defaultPattern, ...this.initialPattern,};
			},
		},
		computed: {
			serviceOptions: function () {
				let services = this.$store.state.services;
				let options = services.map(service => {
					return {
						value: service.port,
						text: `${service.name} #${service.port}`,
					}
				});

				return [
						{ value: null, text: 'Any service', },
						...options,
				];
			},
		},
		methods: {
			onSearchTargetChange(target) {
				if (target !== 'CONTENT') this.newPattern.searchType = 'REGEX';
			},
			getPlaceholder() {
				if (this.newPattern.searchTarget && this.newPattern.searchTarget !== 'CONTENT') return '^10\\.20\\.30\\.[0-9]+$';
				if (this.newPattern.searchType === 'REGEX') return '[A-Z0-9]{31}=';
				else if (this.newPattern.searchType === 'SUBSTRING') return 'HTTP/2';
				else return 'DEAD BEEF 1337';
			},
			validateKey(e) {
				console.log('', e);
				if (this.newPattern.searchType !== 'SUBBYTES') return;
				if (!hexRegex.test(e?.key)) {
					e.preventDefault();
				}
			},

			checkValidity() {
				return this.$refs.addPatternForm.reportValidity();
			},

			reset() {
				this.newPattern = {...defaultPattern,};
			},

			addPattern(ev) {
				ev.preventDefault();
				if (!this.checkValidity()) {
					console.debug('Form is invalid');
					return;
				}
				console.debug('Adding/editing pattern...', this.newPattern);

				if (this.newPattern.searchType === 'SUBBYTES') {
					this.newPattern.value = this.newPattern.value.replace(/\s+/g, '').toLowerCase();
				}

				let url;
				if (this.creating) {
					url = 'pattern/'
				} else {
					url = 'pattern/' + this.newPattern.id
				}

				this.$http.post(url, this.newPattern)
					.then(response => {
						const data = response.data;
						console.debug('Done adding/editing pattern', data);
						this.$emit('patternAddComplete');
						this.reset();
						this.$bvModal.hide('patternModal');
					}).catch(e => {
					this.$bvToast.toast(`Failed to add pattern: ${e}`, {
						title: 'Error',
						variant: 'danger',
					});
					console.error('Failed to add pattern', e);
				});
			},
		},
	};
</script>
