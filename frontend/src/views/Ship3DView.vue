<template>
  <div class="ship-3d-view">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button-group>
          <el-button :type="viewMode === 'perspective' ? 'primary' : ''" @click="setViewMode('perspective')">
            <el-icon><View /></el-icon>
            透视
          </el-button>
          <el-button :type="viewMode === 'front' ? 'primary' : ''" @click="setViewMode('front')">
            <el-icon><Aim /></el-icon>
            正前
          </el-button>
          <el-button :type="viewMode === 'side' ? 'primary' : ''" @click="setViewMode('side')">
            <el-icon><Rank /></el-icon>
            侧视
          </el-button>
          <el-button :type="viewMode === 'top' ? 'primary' : ''" @click="setViewMode('top')">
            <el-icon><Top /></el-icon>
            俯视
          </el-button>
        </el-button-group>
      </div>
      <div class="toolbar-center">
        <el-tag :type="getStabilityType()" effect="dark" size="large">
          GM: {{ stabilityData?.gmValue?.toFixed(3) || '--' }} m
        </el-tag>
        <el-tag type="info" effect="dark" size="large">
          横摇: {{ sensorData?.rollAngle?.toFixed(1) || '--' }}°
        </el-tag>
        <el-tag type="info" effect="dark" size="large">
          纵摇: {{ sensorData?.pitchAngle?.toFixed(1) || '--' }}°
        </el-tag>
      </div>
      <div class="toolbar-right">
        <el-switch v-model="showWireframe" active-text="线框" inactive-text="实体" />
        <el-switch v-model="showWater" active-text="水面开" inactive-text="水面关" :model-value="true" />
        <el-switch v-model="autoRotate" active-text="自动旋转" inactive-text="手动" />
      </div>
    </div>

    <div class="canvas-container" ref="canvasContainer">
      <canvas ref="canvas"></canvas>
    </div>

    <div class="legend-panel">
      <h4 class="legend-title">
        <el-icon><Picture /></el-icon>
        货物图例
      </h4>
      <div class="legend-items">
        <div class="legend-item" v-for="type in cargoTypes" :key="type.id">
          <span class="legend-color" :style="{ backgroundColor: type.colorCode }"></span>
          <span class="legend-name">{{ type.typeName }}</span>
          <span class="legend-desc">{{ type.description }}</span>
        </div>
      </div>
      <div class="cargo-summary">
        <h4 class="legend-title">
          <el-icon><DataAnalysis /></el-icon>
          装载统计
        </h4>
        <div class="summary-grid">
          <div class="summary-item">
            <span class="summary-label">总载重</span>
            <span class="summary-value">{{ loadingSummary?.totalWeight?.toFixed(1) || 0 }} t</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">利用率</span>
            <span class="summary-value">{{ loadingSummary?.utilizationPercent?.toFixed(1) || 0 }}%</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">粮</span>
            <span class="summary-value grain">{{ loadingSummary?.grainWeight?.toFixed(1) || 0 }} t</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">盐</span>
            <span class="summary-value salt">{{ loadingSummary?.saltWeight?.toFixed(1) || 0 }} t</span>
          </div>
        </div>
      </div>
    </div>

    <div class="info-overlay" v-if="hoveredHold">
      <div class="overlay-title">{{ hoveredHold.holdName }}</div>
      <div class="overlay-content">
        <div class="overlay-row">
          <span>位置:</span>
          <span>X: {{ hoveredHold.cgX?.toFixed(2) }}m, Y: {{ hoveredHold.cgY?.toFixed(2) }}m, Z: {{ hoveredHold.cgZ?.toFixed(2) }}m</span>
        </div>
        <div class="overlay-row">
          <span>最大载重:</span>
          <span>{{ hoveredHold.maxWeight }} t</span>
        </div>
        <div class="overlay-row">
          <span>容积:</span>
          <span>{{ hoveredHold.capacityVolume }} m³</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { View, Aim, Rank, Top, Picture, DataAnalysis } from '@element-plus/icons-vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { useShipData } from '@/composables/useShipData'
import { getCargoTypes } from '@/api/loading'

const props = defineProps({
  shipId: {
    type: String,
    default: null
  }
})

const shipIdRef = computed(() => props.shipId)
const { sensorData, stabilityData, loadingSummary, cargoLoadings, cargoHolds } = useShipData(shipIdRef)

const canvasContainer = ref(null)
const canvas = ref(null)
const viewMode = ref('perspective')
const showWireframe = ref(false)
const showWater = ref(true)
const autoRotate = ref(false)
const cargoTypes = ref([])
const hoveredHold = ref(null)

let scene = null
let camera = null
let renderer = null
let controls = null
let shipGroup = null
let waterMesh = null
let cargoMeshes = []
let holdMeshes = []
let animationId = null
let raycaster = null
let mouse = null
let clock = null

const getStabilityType = () => {
  const gm = stabilityData.value?.gmValue
  if (gm == null) return 'info'
  if (gm < 0.3) return 'danger'
  if (gm < 0.5) return 'warning'
  return 'success'
}

const initThree = () => {
  if (!canvasContainer.value || !canvas.value) return

  clock = new THREE.Clock()
  raycaster = new THREE.Raycaster()
  mouse = new THREE.Vector2()

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0c1929)
  scene.fog = new THREE.FogExp2(0x0c1929, 0.008)

  const containerRect = canvasContainer.value.getBoundingClientRect()
  const width = containerRect.width
  const height = containerRect.height

  camera = new THREE.PerspectiveCamera(60, width / height, 0.1, 2000)
  camera.position.set(60, 40, 60)

  renderer = new THREE.WebGLRenderer({
    canvas: canvas.value,
    antialias: true,
    alpha: true
  })
  renderer.setSize(width, height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.2

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.minDistance = 20
  controls.maxDistance = 200
  controls.maxPolarAngle = Math.PI / 2 - 0.1

  const ambientLight = new THREE.AmbientLight(0x404060, 0.6)
  scene.add(ambientLight)

  const directionalLight = new THREE.DirectionalLight(0xffffff, 1.2)
  directionalLight.position.set(80, 100, 60)
  directionalLight.castShadow = true
  directionalLight.shadow.mapSize.width = 2048
  directionalLight.shadow.mapSize.height = 2048
  directionalLight.shadow.camera.near = 0.5
  directionalLight.shadow.camera.far = 500
  directionalLight.shadow.camera.left = -100
  directionalLight.shadow.camera.right = 100
  directionalLight.shadow.camera.top = 100
  directionalLight.shadow.camera.bottom = -100
  scene.add(directionalLight)

  const fillLight = new THREE.DirectionalLight(0x409EFF, 0.4)
  fillLight.position.set(-60, 40, -40)
  scene.add(fillLight)

  createWater()
  createShip()
  createSkyDome()

  animate()

  window.addEventListener('resize', onWindowResize)
  canvas.value.addEventListener('mousemove', onMouseMove)
}

const createSkyDome = () => {
  const skyGeometry = new THREE.SphereGeometry(800, 32, 32)
  const skyMaterial = new THREE.ShaderMaterial({
    uniforms: {
      topColor: { value: new THREE.Color(0x0a1628) },
      bottomColor: { value: new THREE.Color(0x1a365d) },
      offset: { value: 100 },
      exponent: { value: 0.6 }
    },
    vertexShader: `
      varying vec3 vWorldPosition;
      void main() {
        vec4 worldPosition = modelMatrix * vec4(position, 1.0);
        vWorldPosition = worldPosition.xyz;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      uniform vec3 topColor;
      uniform vec3 bottomColor;
      uniform float offset;
      uniform float exponent;
      varying vec3 vWorldPosition;
      void main() {
        float h = normalize(vWorldPosition + offset).y;
        gl_FragColor = vec4(mix(bottomColor, topColor, max(pow(max(h, 0.0), exponent), 0.0)), 1.0);
      }
    `,
    side: THREE.BackSide
  })
  const sky = new THREE.Mesh(skyGeometry, skyMaterial)
  scene.add(sky)
}

const createWater = () => {
  const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
  const segments = isMobile ? 32 : 64
  
  const waterGeometry = new THREE.PlaneGeometry(800, 800, segments, segments)
  
  const waterMaterial = new THREE.ShaderMaterial({
    uniforms: {
      time: { value: 0 },
      waterColor: { value: new THREE.Color(0x0077be) },
      foamColor: { value: new THREE.Color(0xffffff) },
      depthColor: { value: new THREE.Color(0x001a33) },
      waveParams: {
        value: [
          new THREE.Vector4(0.02, 0.8, 0.0, 0.6),
          new THREE.Vector4(0.03, 0.5, 1.5, 0.4),
          new THREE.Vector4(0.015, 0.3, 3.0, 0.3)
        ]
      }
    },
    vertexShader: `
      uniform float time;
      uniform vec4 waveParams[3];
      varying vec2 vUv;
      varying float vElevation;
      varying vec3 vNormal;
      
      vec3 gerstnerWave(vec2 pos, float steepness, float wavelength, float speed, float direction, float phase) {
        float k = 2.0 * 3.14159 / wavelength;
        float c = sqrt(9.8 / k);
        vec2 d = normalize(vec2(cos(direction), sin(direction)));
        float f = k * (dot(d, pos) - c * speed * time + phase);
        float a = steepness / k;
        
        return vec3(
          d.x * a * cos(f),
          a * sin(f),
          d.y * a * cos(f)
        );
      }
      
      void main() {
        vUv = uv;
        vec3 pos = position;
        
        vec3 waveOffset = vec3(0.0);
        vec3 normalOffset = vec3(0.0);
        
        for (int i = 0; i < 3; i++) {
          vec4 wp = waveParams[i];
          float dir = float(i) * 2.094;
          vec3 wave = gerstnerWave(pos.xy, wp.w, 1.0 / wp.x, wp.y, dir, wp.z);
          waveOffset += wave;
          
          float k = 2.0 * 3.14159 * wp.x;
          float c = sqrt(9.8 / k);
          vec2 d = normalize(vec2(cos(dir), sin(dir)));
          float f = k * (dot(d, pos.xy) - c * wp.y * time + wp.z);
          normalOffset += vec3(
            -d.x * wp.w * cos(f),
            wp.w * sin(f),
            -d.y * wp.w * cos(f)
          );
        }
        
        pos += waveOffset;
        vElevation = pos.z;
        vNormal = normalize(vec3(0.0, 1.0, 0.0) - normalOffset);
        
        gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
      }
    `,
    fragmentShader: `
      uniform float time;
      uniform vec3 waterColor;
      uniform vec3 foamColor;
      uniform vec3 depthColor;
      varying vec2 vUv;
      varying float vElevation;
      varying vec3 vNormal;
      
      void main() {
        vec3 lightDir = normalize(vec3(0.5, 0.8, 0.6));
        float diffuse = max(dot(vNormal, lightDir), 0.0);
        
        float depthFactor = smoothstep(-0.5, 1.0, vElevation);
        vec3 baseColor = mix(depthColor, waterColor, depthFactor);
        
        float foamThreshold = 0.5;
        float foamAmount = smoothstep(foamThreshold - 0.1, foamThreshold + 0.1, vElevation);
        baseColor = mix(baseColor, foamColor, foamAmount * 0.5);
        
        vec3 viewDir = normalize(vec3(0.0, 1.0, 1.0));
        vec3 reflectDir = reflect(-lightDir, vNormal);
        float specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
        
        vec3 finalColor = baseColor * (0.55 + diffuse * 0.45) + specular * 0.6;
        
        float fresnel = pow(1.0 - max(dot(vNormal, viewDir), 0.0), 2.0);
        finalColor = mix(finalColor, vec3(0.3, 0.6, 0.9), fresnel * 0.3);
        
        gl_FragColor = vec4(finalColor, 0.85);
      }
    `,
    transparent: true,
    side: THREE.DoubleSide
  })

  waterMesh = new THREE.Mesh(waterGeometry, waterMaterial)
  waterMesh.rotation.x = -Math.PI / 2
  waterMesh.position.y = 0
  waterMesh.receiveShadow = true
  scene.add(waterMesh)
}

const createShip = () => {
  shipGroup = new THREE.Group()
  
  const shipLength = 50
  const shipWidth = 12
  const shipHeight = 6
  
  const hullMaterial = new THREE.MeshStandardMaterial({
    color: 0x8B4513,
    roughness: 0.7,
    metalness: 0.1
  })
  
  const deckMaterial = new THREE.MeshStandardMaterial({
    color: 0x654321,
    roughness: 0.8,
    metalness: 0.05
  })
  
  const cabinMaterial = new THREE.MeshStandardMaterial({
    color: 0x4a3728,
    roughness: 0.6,
    metalness: 0.1
  })

  const hullShape = new THREE.Shape()
  hullShape.moveTo(-shipLength / 2, 0)
  hullShape.quadraticCurveTo(-shipLength / 2 + 3, -shipHeight * 0.8, -shipLength / 4, -shipHeight)
  hullShape.lineTo(shipLength / 4, -shipHeight)
  hullShape.quadraticCurveTo(shipLength / 2 - 3, -shipHeight * 0.8, shipLength / 2, 0)
  hullShape.lineTo(shipLength / 2, shipHeight * 0.3)
  hullShape.lineTo(-shipLength / 2, shipHeight * 0.3)
  hullShape.lineTo(-shipLength / 2, 0)

  const hullExtrudeSettings = {
    steps: 1,
    depth: shipWidth,
    bevelEnabled: true,
    bevelThickness: 0.5,
    bevelSize: 0.3,
    bevelSegments: 4
  }

  const hullGeometry = new THREE.ExtrudeGeometry(hullShape, hullExtrudeSettings)
  hullGeometry.center()
  const hullMesh = new THREE.Mesh(hullGeometry, hullMaterial)
  hullMesh.rotation.y = Math.PI / 2
  hullMesh.castShadow = true
  hullMesh.receiveShadow = true
  shipGroup.add(hullMesh)

  const deckGeometry = new THREE.BoxGeometry(shipLength * 0.95, 0.3, shipWidth * 0.95)
  const deckMesh = new THREE.Mesh(deckGeometry, deckMaterial)
  deckMesh.position.y = shipHeight * 0.3 + 0.15
  deckMesh.castShadow = true
  deckMesh.receiveShadow = true
  shipGroup.add(deckMesh)

  const bowGeometry = new THREE.ConeGeometry(shipWidth * 0.4, 8, 8)
  const bowMesh = new THREE.Mesh(bowGeometry, cabinMaterial)
  bowMesh.rotation.z = Math.PI / 2
  bowMesh.position.set(shipLength / 2 - 2, shipHeight * 0.5, 0)
  bowMesh.castShadow = true
  shipGroup.add(bowMesh)

  const cabinGeometry = new THREE.BoxGeometry(12, 3.5, shipWidth * 0.75)
  const cabinMesh = new THREE.Mesh(cabinGeometry, cabinMaterial)
  cabinMesh.position.set(-5, shipHeight * 0.3 + 0.3 + 1.75, 0)
  cabinMesh.castShadow = true
  cabinMesh.receiveShadow = true
  shipGroup.add(cabinMesh)

  const roofGeometry = new THREE.BoxGeometry(13, 0.4, shipWidth * 0.85)
  const roofMesh = new THREE.Mesh(roofGeometry, cabinMaterial)
  roofMesh.position.set(-5, shipHeight * 0.3 + 0.3 + 3.5 + 0.2, 0)
  roofMesh.castShadow = true
  shipGroup.add(roofMesh)

  const mastGeometry = new THREE.CylinderGeometry(0.2, 0.3, 18, 8)
  const mastMaterial = new THREE.MeshStandardMaterial({ color: 0x3d2817, roughness: 0.9 })
  const mast1 = new THREE.Mesh(mastGeometry, mastMaterial)
  mast1.position.set(8, shipHeight * 0.3 + 0.3 + 9, 0)
  mast1.castShadow = true
  shipGroup.add(mast1)

  const mast2 = new THREE.Mesh(mastGeometry, mastMaterial)
  mast2.position.set(-12, shipHeight * 0.3 + 0.3 + 9, 0)
  mast2.castShadow = true
  shipGroup.add(mast2)

  const sailGeometry = new THREE.PlaneGeometry(10, 14)
  const sailMaterial = new THREE.MeshStandardMaterial({
    color: 0xf5f5dc,
    roughness: 0.9,
    side: THREE.DoubleSide,
    transparent: true,
    opacity: 0.9
  })
  const sail1 = new THREE.Mesh(sailGeometry, sailMaterial)
  sail1.position.set(8, shipHeight * 0.3 + 0.3 + 9, 0.1)
  sail1.rotation.y = 0.2
  shipGroup.add(sail1)

  const sail2 = new THREE.Mesh(sailGeometry, sailMaterial)
  sail2.position.set(-12, shipHeight * 0.3 + 0.3 + 9, 0.1)
  sail2.rotation.y = -0.15
  shipGroup.add(sail2)

  const railingMaterial = new THREE.MeshStandardMaterial({ color: 0x2d1810, roughness: 0.8 })
  for (let i = 0; i < 4; i++) {
    const postGeometry = new THREE.CylinderGeometry(0.08, 0.08, 1.2, 6)
    const post1 = new THREE.Mesh(postGeometry, railingMaterial)
    post1.position.set(-shipLength / 2 + 5 + i * 10, shipHeight * 0.3 + 0.3 + 0.6, shipWidth * 0.4)
    post1.castShadow = true
    shipGroup.add(post1)
    
    const post2 = new THREE.Mesh(postGeometry, railingMaterial)
    post2.position.set(-shipLength / 2 + 5 + i * 10, shipHeight * 0.3 + 0.3 + 0.6, -shipWidth * 0.4)
    post2.castShadow = true
    shipGroup.add(post2)
  }

  const railGeometry = new THREE.BoxGeometry(shipLength * 0.8, 0.06, 0.06)
  const rail1 = new THREE.Mesh(railGeometry, railingMaterial)
  rail1.position.set(0, shipHeight * 0.3 + 0.3 + 1.0, shipWidth * 0.4)
  shipGroup.add(rail1)
  const rail2 = new THREE.Mesh(railGeometry, railingMaterial)
  rail2.position.set(0, shipHeight * 0.3 + 0.3 + 1.0, -shipWidth * 0.4)
  shipGroup.add(rail2)

  createCargoHolds(shipLength, shipWidth, shipHeight)

  shipGroup.position.y = -1
  scene.add(shipGroup)
}

const createCargoHolds = (shipLength, shipWidth, shipHeight) => {
  const holdPositions = [
    { x: 15, name: '首舱' },
    { x: 5, name: '前舱' },
    { x: -5, name: '中舱' },
    { x: -15, name: '尾舱' }
  ]

  const holdWidth = shipWidth * 0.7
  const holdDepth = shipHeight * 0.5

  holdPositions.forEach((pos, index) => {
    const holdGeometry = new THREE.BoxGeometry(8, holdDepth, holdWidth)
    const holdMaterial = new THREE.MeshStandardMaterial({
      color: 0x3d2817,
      roughness: 0.8,
      transparent: true,
      opacity: 0.3,
      wireframe: false
    })
    
    const holdMesh = new THREE.Mesh(holdGeometry, holdMaterial)
    holdMesh.position.set(pos.x, shipHeight * 0.3 + 0.15 - holdDepth / 2, 0)
    holdMesh.userData = { holdId: cargoHolds.value[index]?.id, holdName: pos.name, holdIndex: index }
    holdMesh.receiveShadow = true
    shipGroup.add(holdMesh)
    holdMeshes.push(holdMesh)

    const edgeGeometry = new THREE.EdgesGeometry(holdGeometry)
    const edgeMaterial = new THREE.LineBasicMaterial({ color: 0x409EFF, linewidth: 2 })
    const edges = new THREE.LineSegments(edgeGeometry, edgeMaterial)
    edges.position.copy(holdMesh.position)
    shipGroup.add(edges)
  })
}

const updateCargoBlocks = () => {
  cargoMeshes.forEach(mesh => shipGroup.remove(mesh))
  cargoMeshes = []

  if (!cargoLoadings.value || cargoLoadings.value.length === 0) return

  const shipHeight = 6
  const holdDepth = shipHeight * 0.5

  cargoLoadings.value.forEach(loading => {
    const holdIndex = cargoHolds.value.findIndex(h => h.id === loading.cargoHoldId)
    if (holdIndex === -1) return

    const holdPositions = [15, 5, -5, -15]
    const holdX = holdPositions[holdIndex]
    const holdWidth = 12 * 0.7
    const holdLength = 8

    const maxWeight = cargoHolds.value[holdIndex]?.maxWeight || 100
    const fillRatio = Math.min((loading.weight || 0) / maxWeight, 1)
    const blockHeight = (holdDepth - 0.2) * fillRatio

    if (blockHeight <= 0.1) return

    const cargoColor = loading.cargoColor || '#409EFF'
    const color = new THREE.Color(cargoColor)

    const cargoGeometry = new THREE.BoxGeometry(holdLength - 0.4, blockHeight, holdWidth - 0.4)
    const cargoMaterial = new THREE.MeshStandardMaterial({
      color: color,
      roughness: 0.7,
      metalness: 0.1
    })

    const cargoMesh = new THREE.Mesh(cargoGeometry, cargoMaterial)
    cargoMesh.position.set(holdX, shipHeight * 0.3 + 0.15 - holdDepth + blockHeight / 2 + 0.1, 0)
    cargoMesh.castShadow = true
    cargoMesh.receiveShadow = true
    cargoMesh.userData = {
      cargoName: loading.cargoTypeName,
      weight: loading.weight,
      holdName: cargoHolds.value[holdIndex]?.holdName
    }
    shipGroup.add(cargoMesh)
    cargoMeshes.push(cargoMesh)
  })
}

const setViewMode = (mode) => {
  viewMode.value = mode
  controls.reset()
  
  const shipCenter = new THREE.Vector3(0, 2, 0)
  
  switch (mode) {
    case 'perspective':
      camera.position.set(60, 40, 60)
      break
    case 'front':
      camera.position.set(80, 15, 0)
      break
    case 'side':
      camera.position.set(0, 15, 80)
      break
    case 'top':
      camera.position.set(0, 100, 0.01)
      break
  }
  
  controls.target.copy(shipCenter)
  controls.update()
}

const onWindowResize = () => {
  if (!container) return
  const containerRect = canvasContainer.value.getBoundingClientRect()
  camera.aspect = containerRect.width / containerRect.height
  camera.updateProjectionMatrix()
  renderer.setSize(containerRect.width, containerRect.height)
}

const onMouseMove = (event) => {
  const rect = canvas.value.getBoundingClientRect()
  mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

  raycaster.setFromCamera(mouse, camera)
  const intersects = raycaster.intersectObjects([...holdMeshes, ...cargoMeshes])

  if (intersects.length > 0) {
    const obj = intersects[0].object
    if (obj.userData.holdId) {
      const hold = cargoHolds.value.find(h => h.id === obj.userData.holdId)
      hoveredHold.value = { ...hold, holdName: obj.userData.holdName }
    } else if (obj.userData.cargoName) {
      hoveredHold.value = {
        holdName: obj.userData.holdName,
        cargoName: obj.userData.cargoName,
        weight: obj.userData.weight
      }
    }
    document.body.style.cursor = 'pointer'
  } else {
    hoveredHold.value = null
    document.body.style.cursor = 'grab'
  }
}

const animate = () => {
  animationId = requestAnimationFrame(animate)
  
  const elapsedTime = clock.getElapsedTime()

  if (waterMesh && waterMesh.material.uniforms) {
    waterMesh.material.uniforms.time.value = elapsedTime
  }

  if (shipGroup) {
    const rollAngle = sensorData.value?.rollAngle || 0
    const pitchAngle = sensorData.value?.pitchAngle || 0
    
    const targetRoll = (rollAngle * Math.PI) / 180
    const targetPitch = (pitchAngle * Math.PI) / 180
    
    const waveOffset = Math.sin(elapsedTime * 0.5) * 0.02
    const waveSway = Math.sin(elapsedTime * 0.3) * 0.01
    
    shipGroup.rotation.z += (targetRoll + waveSway - shipGroup.rotation.z) * 0.05
    shipGroup.rotation.x += (targetPitch + waveOffset - shipGroup.rotation.x) * 0.05
    shipGroup.position.y = -1 + Math.sin(elapsedTime * 0.8) * 0.1
  }

  if (autoRotate.value && controls) {
    controls.autoRotate = true
    controls.autoRotateSpeed = 0.5
  } else if (controls) {
    controls.autoRotate = false
  }

  if (showWireframe.value) {
    holdMeshes.forEach(mesh => {
      if (mesh.material) mesh.material.wireframe = true
    })
    cargoMeshes.forEach(mesh => {
      if (mesh.material) mesh.material.wireframe = true
    })
  } else {
    holdMeshes.forEach(mesh => {
      if (mesh.material) mesh.material.wireframe = false
    })
    cargoMeshes.forEach(mesh => {
      if (mesh.material) mesh.material.wireframe = false
    })
  }

  if (waterMesh) {
    waterMesh.visible = showWater.value
  }

  controls.update()
  renderer.render(scene, camera)
}

const fetchCargoTypes = async () => {
  try {
    const res = await getCargoTypes()
    cargoTypes.value = res.data || []
  } catch (e) {
    console.error('获取货物类型失败', e)
  }
}

watch(cargoLoadings, () => {
  updateCargoBlocks()
}, { deep: true })

watch(cargoHolds, () => {
  if (shipGroup) {
    holdMeshes.forEach(mesh => shipGroup.remove(mesh))
    holdMeshes = []
    createCargoHolds(50, 12, 6)
    updateCargoBlocks()
  }
})

onMounted(() => {
  fetchCargoTypes()
  setTimeout(() => {
    initThree()
    updateCargoBlocks()
  }, 100)
})

onUnmounted(() => {
  if (animationId) cancelAnimationFrame(animationId)
  if (renderer) renderer.dispose()
  window.removeEventListener('resize', onWindowResize)
  if (canvas.value) {
    canvas.value.removeEventListener('mousemove', onMouseMove)
  }
  document.body.style.cursor = 'default'
})
</script>

<style scoped lang="scss">
.ship-3d-view {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.toolbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: rgba(12, 25, 41, 0.95);
  border-bottom: 1px solid rgba(64, 158, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 10;
  backdrop-filter: blur(10px);

  .toolbar-left,
  .toolbar-center,
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .toolbar-center {
    gap: 16px;
  }
}

.canvas-container {
  position: absolute;
  top: 56px;
  left: 0;
  right: 0;
  bottom: 0;

  canvas {
    width: 100%;
    height: 100%;
    display: block;
  }
}

.legend-panel {
  position: absolute;
  right: 20px;
  top: 80px;
  width: 280px;
  background: rgba(12, 25, 41, 0.9);
  border: 1px solid rgba(64, 158, 255, 0.3);
  border-radius: 10px;
  padding: 16px;
  backdrop-filter: blur(10px);
  z-index: 10;

  .legend-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #ffffff;
    font-size: 14px;
    margin: 0 0 12px 0;

    .el-icon {
      color: #409EFF;
    }
  }

  .legend-items {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 16px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .legend-color {
        width: 16px;
        height: 16px;
        border-radius: 3px;
        flex-shrink: 0;
      }

      .legend-name {
        color: #ffffff;
        font-size: 13px;
        font-weight: 500;
      }

      .legend-desc {
        color: #a0aec0;
        font-size: 11px;
        margin-left: auto;
      }
    }
  }

  .cargo-summary {
    border-top: 1px solid rgba(64, 158, 255, 0.2);
    padding-top: 12px;

    .summary-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;

      .summary-item {
        background: rgba(0, 0, 0, 0.2);
        padding: 8px 10px;
        border-radius: 6px;
        text-align: center;

        .summary-label {
          display: block;
          color: #a0aec0;
          font-size: 11px;
          margin-bottom: 4px;
        }

        .summary-value {
          display: block;
          color: #409EFF;
          font-size: 14px;
          font-weight: bold;
          font-family: 'Courier New', monospace;

          &.grain { color: #E6A23C; }
          &.salt { color: #909399; }
        }
      }
    }
  }
}

.info-overlay {
  position: absolute;
  bottom: 20px;
  left: 20px;
  background: rgba(12, 25, 41, 0.95);
  border: 1px solid rgba(64, 158, 255, 0.4);
  border-radius: 8px;
  padding: 12px 16px;
  backdrop-filter: blur(10px);
  z-index: 10;
  min-width: 240px;

  .overlay-title {
    color: #409EFF;
    font-size: 15px;
    font-weight: bold;
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid rgba(64, 158, 255, 0.2);
  }

  .overlay-content {
    .overlay-row {
      display: flex;
      justify-content: space-between;
      padding: 4px 0;
      font-size: 13px;

      span:first-child {
        color: #a0aec0;
      }

      span:last-child {
        color: #ffffff;
        font-family: 'Courier New', monospace;
      }
    }
  }
}
</style>
