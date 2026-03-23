(function attachClientToolRegistry(global) {
    class ClientToolRegistry {
        constructor() {
            this._tools = new Map();
        }

        registerTool(toolDef) {
            this._validateToolDefinition(toolDef);
            const normalized = {
                ...toolDef,
                inputSchema: toolDef.inputSchema || { type: 'object' }
            };
            this._tools.set(normalized.id, normalized);
            return normalized;
        }

        getTool(toolId) {
            return this._tools.get(toolId) || null;
        }

        listTools() {
            return Array.from(this._tools.values()).map((tool) => ({
                id: tool.id,
                name: tool.name,
                description: tool.description,
                inputSchema: tool.inputSchema
            }));
        }

        entries() {
            return Array.from(this._tools.entries());
        }

        async invokeTool(toolId, args) {
            const tool = this.getTool(toolId);
            if (!tool) {
                return this._buildErrorResult('TOOL_NOT_FOUND', `Tool not found: ${toolId}`);
            }
            if (tool.enabled === false) {
                return this._buildErrorResult('TOOL_DISABLED', `Tool disabled: ${toolId}`);
            }

            try {
                this._validateInput(args, tool.inputSchema);
            } catch (error) {
                return this._buildErrorResult('INVALID_ARGUMENTS', error.message);
            }

            try {
                const data = await Promise.resolve(tool.handler(args || {}, tool));
                return { ok: true, data, error: null };
            } catch (error) {
                console.error('[ClientToolRegistry] invoke error', toolId, error);
                return this._buildErrorResult('TOOL_EXECUTION_ERROR', error?.message || 'Tool execution failed');
            }
        }

        _validateToolDefinition(toolDef) {
            if (!toolDef || typeof toolDef !== 'object') {
                throw new Error('toolDef must be an object');
            }
            if (!toolDef.id || typeof toolDef.id !== 'string') {
                throw new Error('toolDef.id must be a non-empty string');
            }
            if (!toolDef.name || typeof toolDef.name !== 'string') {
                throw new Error('toolDef.name must be a non-empty string');
            }
            if (!toolDef.description || typeof toolDef.description !== 'string') {
                throw new Error('toolDef.description must be a non-empty string');
            }
            if (toolDef.inputSchema && typeof toolDef.inputSchema !== 'object') {
                throw new Error('toolDef.inputSchema must be an object');
            }
            if (typeof toolDef.handler !== 'function') {
                throw new Error('toolDef.handler must be a function');
            }
        }

        _validateInput(args, schema = {}) {
            if (schema.type === 'object' && (args == null || Array.isArray(args) || typeof args !== 'object')) {
                throw new Error('Arguments must be an object');
            }

            const required = Array.isArray(schema.required) ? schema.required : [];
            for (const field of required) {
                if (args == null || !(field in args)) {
                    throw new Error(`Missing required argument: ${field}`);
                }
            }

            const properties = schema.properties || {};
            for (const [key, rule] of Object.entries(properties)) {
                if (args == null || !(key in args) || !rule || !rule.type) continue;
                const value = args[key];
                if (value === null) {
                    throw new Error(`Argument ${key} cannot be null`);
                }

                if (rule.type === 'integer') {
                    if (!(typeof value === 'number' && Number.isInteger(value))) {
                        throw new Error(`Argument ${key} should be integer`);
                    }
                    continue;
                }

                const actualType = Array.isArray(value) ? 'array' : typeof value;
                if (actualType !== rule.type) {
                    throw new Error(`Argument ${key} should be ${rule.type}`);
                }
            }
        }

        _buildErrorResult(code, message) {
            return {
                ok: false,
                data: null,
                error: { code, message }
            };
        }
    }

    global.clientToolRegistry = global.clientToolRegistry || new ClientToolRegistry();
})(window);
