const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const compiler = require('vue-template-compiler');

// Exercise the actual component methods without a browser or an extra test framework.
function component(relativePath) {
    const file = fs.readFileSync(path.join(__dirname, '../src', relativePath), 'utf8');
    const source = compiler.parseComponent(file).script.content
        .replace(/^\s*import .*$/gm, '')
        .replace('export default', 'module.exports =');
    const sandbox = {module: {exports: {}}, SidebarStream: {}, PatternModal: {}, LookBack: {}, console};
    vm.runInNewContext(source, sandbox);
    return sandbox.module.exports;
}

test('IP selection uses regex and provides an escaped IPv4 example', () => {
    const methods = component('views/PatternModal.vue').methods;
    const context = {newPattern: {searchTarget: 'SOURCE_IP', searchType: 'SUBBYTES'}};
    methods.onSearchTargetChange.call(context, 'SOURCE_IP');
    assert.equal(context.newPattern.searchType, 'REGEX');
    assert.equal(methods.getPlaceholder.call(context), '^10\\.20\\.30\\.[0-9]+$');
});

test('live filter accepts matching DTO IDs and rejects other streams', () => {
    const addStream = component('components/Sidebar.vue').methods.addStreamFromWs;
    const context = {
        $route: {params: {servicePort: '80'}, query: {pattern: '7'}},
        $store: {state: {displayFavoritesOnly: false, pause: false}},
        streams: [],
    };
    addStream.call(context, {id: 1, service: 80, foundPatternsIds: [7]});
    addStream.call(context, {id: 2, service: 80, foundPatternsIds: [8]});
    addStream.call(context, {id: 3, service: 443, foundPatternsIds: [7]});
    addStream.call(context, {id: 4, service: 80});
    assert.deepEqual(context.streams.map(stream => stream.id), [1]);
});

test('IP matches highlight endpoints without changing payload highlights', () => {
    const methods = component('components/Packet.vue').methods;
    const context = {
        $store: {state: {patterns: [
            {id: 1, color: '#112233', deleted: false},
            {id: 2, color: '#445566', deleted: false},
        ]}},
        packet: {matches: [
            {patternId: 1, searchTarget: 'SOURCE_IP', startPosition: 0, endPosition: 10},
            {patternId: 2, searchTarget: 'CONTENT', startPosition: 0, endPosition: 3},
        ]},
    };
    assert.equal(methods.ipHighlightStyle.call(context, 'SOURCE_IP').backgroundColor, '#112233');
    assert.equal(Object.keys(methods.ipHighlightStyle.call(context, 'DESTINATION_IP')).length, 0);
    assert.equal(methods.highlightPatterns.call(context, 'FLAG rest'),
        '<span style="background-color: #445566">FLAG</span> rest');

    context.packet.matches = [context.packet.matches[0]];
    assert.equal(methods.highlightPatterns.call(context, 'unchanged'), 'unchanged');
    context.$store.state.patterns[0].deleted = true;
    assert.equal(Object.keys(methods.ipHighlightStyle.call(context, 'SOURCE_IP')).length, 0);
});

test('legacy content matches without a target retain their highlights', () => {
    const methods = component('components/Packet.vue').methods;
    const context = {
        $store: {state: {patterns: [{id: 1, color: '#112233', deleted: false}]}},
        packet: {matches: [{patternId: 1, startPosition: 0, endPosition: 3}]},
    };
    assert.equal(methods.highlightPatterns.call(context, 'FLAG'),
        '<span style="background-color: #112233">FLAG</span>');
});

test('pattern list identifies the selected IP field', () => {
    const method = component('components/PatternsDropdown.vue').methods.getSearchTargetLabel;
    assert.equal(method('SOURCE_IP'), 'source IP');
    assert.equal(method('DESTINATION_IP'), 'destination IP');
    assert.equal(method('EITHER_IP'), 'either IP address');
});
