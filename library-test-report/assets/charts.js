(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var sevCritical = style.getPropertyValue('--sev-critical').trim();
  var sevHigh = style.getPropertyValue('--sev-high').trim();
  var sevMedium = style.getPropertyValue('--sev-medium').trim();
  var sevLow = style.getPropertyValue('--sev-low').trim();
  var ok = style.getPropertyValue('--ok').trim();

  var baseText = { color: muted, fontSize: 12 };

  // --- Chart: severity ---
  var severity = echarts.init(document.getElementById('chart-severity'), null, { renderer: 'svg' });
  severity.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
    grid: { left: 64, right: 32, top: 16, bottom: 28 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: baseText,
      splitLine: { lineStyle: { color: rule } },
      axisLine: { lineStyle: { color: rule } }
    },
    yAxis: {
      type: 'category',
      data: ['严重', '高', '中', '低'],
      axisLabel: { color: ink, fontSize: 12, fontWeight: 600 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      barWidth: 22,
      data: [
        { value: 5, itemStyle: { color: sevCritical } },
        { value: 5, itemStyle: { color: sevHigh } },
        { value: 7, itemStyle: { color: sevMedium } },
        { value: 4, itemStyle: { color: sevLow } }
      ],
      label: {
        show: true,
        position: 'right',
        color: ink,
        fontWeight: 700,
        fontSize: 12
      }
    }]
  });
  window.addEventListener('resize', function () { severity.resize(); });

  // --- Chart: category donut ---
  var category = echarts.init(document.getElementById('chart-category'), null, { renderer: 'svg' });
  category.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true, formatter: '{b}<br/>{c} 项（{d}%）' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: muted, fontSize: 12 }
    },
    color: [sevCritical, accent, accent2, sevMedium, sevLow],
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      itemStyle: { borderColor: bg2, borderWidth: 3 },
      label: {
        color: ink,
        fontSize: 12,
        formatter: function (p) { return p.percent + '%'; }
      },
      labelLine: { show: false },
      data: [
        { name: '安全与鉴权', value: 6 },
        { name: '并发与数据一致性', value: 6 },
        { name: '校验与边界', value: 4 },
        { name: '功能与体验', value: 5 }
      ]
    }]
  });
  window.addEventListener('resize', function () { category.resize(); });

  // --- Chart: latency ---
  var latency = echarts.init(document.getElementById('chart-latency'), null, { renderer: 'svg' });
  latency.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' }, valueFormatter: function (v) { return v + ' ms'; } },
    grid: { left: 52, right: 30, top: 20, bottom: 34 },
    xAxis: {
      type: 'category',
      data: ['图书分页\n(30 次均值)', '统计概览', '单次登录'],
      axisLabel: { color: ink, fontSize: 11.5, interval: 0, lineHeight: 16 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: 'ms',
      nameTextStyle: { color: muted, fontSize: 11 },
      axisLabel: baseText,
      splitLine: { lineStyle: { color: rule } },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'bar',
      barWidth: 34,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: accent
      },
      data: [16.9, 21, 70],
      label: {
        show: true,
        position: 'top',
        color: ink,
        fontWeight: 700,
        fontSize: 12,
        formatter: function (p) { return p.value + ' ms'; }
      }
    }]
  });
  window.addEventListener('resize', function () { latency.resize(); });

  // --- Chart: concurrency ---
  var concurrency = echarts.init(document.getElementById('chart-concurrency'), null, { renderer: 'svg' });
  concurrency.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: muted, fontSize: 12 }
    },
    grid: { left: 52, right: 30, top: 20, bottom: 46 },
    xAxis: {
      type: 'category',
      data: ['借阅（库存 1）', '购买（库存 1）'],
      axisLabel: { color: ink, fontSize: 11.5 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      max: 5,
      minInterval: 1,
      axisLabel: baseText,
      splitLine: { lineStyle: { color: rule } },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'total',
        barWidth: 40,
        itemStyle: { color: ok },
        label: { show: true, color: '#fff', fontWeight: 700, fontSize: 12 },
        data: [5, 1]
      },
      {
        name: '失败 / 被拒',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: sevHigh, opacity: 0.85 },
        label: { show: true, color: '#fff', fontWeight: 700, fontSize: 12 },
        data: [0, 4]
      }
    ]
  });
  window.addEventListener('resize', function () { concurrency.resize(); });
})();
