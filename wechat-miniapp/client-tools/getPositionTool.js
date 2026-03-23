const clientToolRegistry = require('../utils/clientToolRegistry');

clientToolRegistry.registerTool({
  id: 'GeoInformation.GetPosition',
  name: '获取地理位置',
  description: '获取当前设备的经纬度与精度信息',
  inputSchema: {
    type: 'object',
    properties: {
      type: { type: 'string' },
      altitude: { type: 'boolean' }
    }
  },
  handler(args = {}) {
    return new Promise((resolve, reject) => {
      wx.getLocation({
        type: args.type || 'wgs84',
        altitude: !!args.altitude,
        success(res) {
          resolve({
            latitude: res.latitude,
            longitude: res.longitude,
            accuracy: res.accuracy,
            altitude: res.altitude,
            verticalAccuracy: res.verticalAccuracy,
            horizontalAccuracy: res.horizontalAccuracy,
            speed: res.speed
          });
        },
        fail(err) {
          reject(new Error((err && err.errMsg) || '获取定位失败'));
        }
      });
    });
  }
});
