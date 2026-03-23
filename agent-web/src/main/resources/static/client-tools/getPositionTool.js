(function registerGetPositionTool(global) {
    const registry = global.clientToolRegistry;
    if (!registry) {
        console.error('[ClientTool] clientToolRegistry is not initialized');
        return;
    }

    registry.registerTool({
        id: 'GeoInformation.GetPosition',
        name: 'Get Position',
        description: 'Get current location information in browser',
        inputSchema: {
            type: 'object',
            properties: {
                highAccuracy: { type: 'boolean' },
                timeout: { type: 'integer' }
            }
        },
        enabled: true,
        mockResponse: {
            header: { namespace: 'GeoInformation', name: 'PositionInfo' },
            payload: {
                errorCode: '0',
                position: { longitude: '116.397', latitude: '39.909', locationSystem: 'WGS84' },
                city: '北京'
            }
        },
        handler(args = {}, tool) {
            if (tool.mockResponse) {
                return tool.mockResponse;
            }
            return new Promise((resolve, reject) => {
                if (!navigator.geolocation) {
                    reject(new Error('Geolocation is not supported by this browser'));
                    return;
                }

                navigator.geolocation.getCurrentPosition(
                    (position) => resolve({
                        header: { namespace: 'GeoInformation', name: 'PositionInfo' },
                        payload: {
                            errorCode: '0',
                            position: {
                                longitude: String(position.coords.longitude),
                                latitude: String(position.coords.latitude),
                                locationSystem: 'WGS84'
                            }
                        }
                    }),
                    (err) => reject(new Error(err?.message || 'Failed to get location')),
                    {
                        enableHighAccuracy: !!args.highAccuracy,
                        timeout: args.timeout || 10000,
                        maximumAge: 0
                    }
                );
            });
        }
    });
})(window);
