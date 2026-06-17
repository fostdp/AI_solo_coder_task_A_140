/**
 * junk_ship_3d.js
 * 古代沙船（方艄）三维渲染模块
 * 功能：Three.js场景初始化、船体模型构建、Gerstner波浪渲染、货物色块、交互控制
 */

import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

export class JunkShip3D {
  constructor(canvas, container, options = {}) {
    this.canvas = canvas
    this.container = container
    this.options = {
      backgroundColor: 0x0c1929,
      fogDensity: 0.008,
      cameraFov: 60,
      cameraNear: 0.1,
      cameraFar: 2000,
      cameraPosition: [60, 40, 60],
      shadowMapSize: 2048,
      mobileSegments: 32,
      desktopSegments: 64,
      ...options
    }

    this.scene = null
    this.camera = null
    this.renderer = null
    this.controls = null
    this.shipGroup = null
    this.waterMesh = null
    this.cargoMeshes = []
    this.holdMeshes = []
    this.animationId = null
    this.raycaster = null
    this.mouse = null
    this.clock = null

    this._viewMode = 'perspective'
    this._showWireframe = false
    this._showWater = true
    this._autoRotate = false
    this._sensorData = null
    this._cargoHolds = []
    this._cargoLoadings = []
    this._onHoldHover = null

    this._isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
      typeof navigator !== 'undefined' ? navigator.userAgent : ''
    )

    this._onResize = this._onResize.bind(this)
    this._onMouseMove = this._onMouseMove.bind(this)
    this._animate = this._animate.bind(this)
  }

  init() {
    if (!this.canvas || !this.container) {
      throw new Error('Canvas和容器元素不能为空')
    }

    this.clock = new THREE.Clock()
    this.raycaster = new THREE.Raycaster()
    this.mouse = new THREE.Vector2()

    this.scene = new THREE.Scene()
    this.scene.background = new THREE.Color(this.options.backgroundColor)
    this.scene.fog = new THREE.FogExp2(this.options.backgroundColor, this.options.fogDensity)

    const containerRect = this.container.getBoundingClientRect()
    const width = containerRect.width
    const height = containerRect.height

    this.camera = new THREE.PerspectiveCamera(
      this.options.cameraFov,
      width / height,
      this.options.cameraNear,
      this.options.cameraFar
    )
    this.camera.position.set(...this.options.cameraPosition)

    this.renderer = new THREE.WebGLRenderer({
      canvas: this.canvas,
      antialias: true,
      alpha: true
    })
    this.renderer.setSize(width, height)
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    this.renderer.shadowMap.enabled = true
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.2

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.05
    this.controls.minDistance = 20
    this.controls.maxDistance = 200
    this.controls.maxPolarAngle = Math.PI / 2 - 0.1

    this._setupLights()
    this._createSkyDome()
    this._createWater()
    this._createShip()

    this._animate()

    window.addEventListener('resize', this._onResize)
    this.canvas.addEventListener('mousemove', this._onMouseMove)
  }

  _setupLights() {
    const ambientLight = new THREE.AmbientLight(0x404060, 0.6)
    this.scene.add(ambientLight)

    const directionalLight = new THREE.DirectionalLight(0xffffff, 1.2)
    directionalLight.position.set(80, 100, 60)
    directionalLight.castShadow = true
    directionalLight.shadow.mapSize.width = this.options.shadowMapSize
    directionalLight.shadow.mapSize.height = this.options.shadowMapSize
    directionalLight.shadow.camera.near = 0.5
    directionalLight.shadow.camera.far = 500
    directionalLight.shadow.camera.left = -100
    directionalLight.shadow.camera.right = 100
    directionalLight.shadow.camera.top = 100
    directionalLight.shadow.camera.bottom = -100
    this.scene.add(directionalLight)

    const fillLight = new THREE.DirectionalLight(0x409EFF, 0.4)
    fillLight.position.set(-60, 40, -40)
    this.scene.add(fillLight)
  }

  _createSkyDome() {
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
    this.scene.add(sky)
  }

  _createWater() {
    const segments = this._isMobile ? this.options.mobileSegments : this.options.desktopSegments
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
      vertexShader: this._getWaterVertexShader(),
      fragmentShader: this._getWaterFragmentShader(),
      transparent: true,
      side: THREE.DoubleSide
    })

    this.waterMesh = new THREE.Mesh(waterGeometry, waterMaterial)
    this.waterMesh.rotation.x = -Math.PI / 2
    this.waterMesh.position.y = 0
    this.waterMesh.receiveShadow = true
    this.scene.add(this.waterMesh)
  }

  _getWaterVertexShader() {
    return `
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
    `
  }

  _getWaterFragmentShader() {
    return `
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
    `
  }

  _createShip() {
    this.shipGroup = new THREE.Group()

    const shipLength = 50
    const shipWidth = 12
    const shipHeight = 6

    const hullMaterial = new THREE.MeshStandardMaterial({
      color: 0x8B4513, roughness: 0.7, metalness: 0.1
    })
    const deckMaterial = new THREE.MeshStandardMaterial({
      color: 0x654321, roughness: 0.8, metalness: 0.05
    })
    const cabinMaterial = new THREE.MeshStandardMaterial({
      color: 0x4a3728, roughness: 0.6, metalness: 0.1
    })

    this._createHull(shipLength, shipWidth, shipHeight, hullMaterial)
    this._createDeckAndStructures(shipLength, shipWidth, shipHeight, deckMaterial, cabinMaterial)
    this._createMastsAndSails(shipLength, shipWidth, shipHeight)
    this._createRailings(shipLength, shipWidth, shipHeight)
    this._createCargoHolds(shipLength, shipWidth, shipHeight)

    this.shipGroup.position.y = -1
    this.scene.add(this.shipGroup)
  }

  _createHull(shipLength, shipWidth, shipHeight, hullMaterial) {
    const hullShape = new THREE.Shape()
    hullShape.moveTo(-shipLength / 2, 0)
    hullShape.quadraticCurveTo(-shipLength / 2 + 3, -shipHeight * 0.8, -shipLength / 4, -shipHeight)
    hullShape.lineTo(shipLength / 4, -shipHeight)
    hullShape.quadraticCurveTo(shipLength / 2 - 3, -shipHeight * 0.8, shipLength / 2, 0)
    hullShape.lineTo(shipLength / 2, shipHeight * 0.3)
    hullShape.lineTo(-shipLength / 2, shipHeight * 0.3)
    hullShape.lineTo(-shipLength / 2, 0)

    const hullExtrudeSettings = {
      steps: 1, depth: shipWidth, bevelEnabled: true,
      bevelThickness: 0.5, bevelSize: 0.3, bevelSegments: 4
    }

    const hullGeometry = new THREE.ExtrudeGeometry(hullShape, hullExtrudeSettings)
    hullGeometry.center()
    const hullMesh = new THREE.Mesh(hullGeometry, hullMaterial)
    hullMesh.rotation.y = Math.PI / 2
    hullMesh.castShadow = true
    hullMesh.receiveShadow = true
    this.shipGroup.add(hullMesh)
  }

  _createDeckAndStructures(shipLength, shipWidth, shipHeight, deckMaterial, cabinMaterial) {
    const deckGeometry = new THREE.BoxGeometry(shipLength * 0.95, 0.3, shipWidth * 0.95)
    const deckMesh = new THREE.Mesh(deckGeometry, deckMaterial)
    deckMesh.position.y = shipHeight * 0.3 + 0.15
    deckMesh.castShadow = true
    deckMesh.receiveShadow = true
    this.shipGroup.add(deckMesh)

    const bowGeometry = new THREE.ConeGeometry(shipWidth * 0.4, 8, 8)
    const bowMesh = new THREE.Mesh(bowGeometry, cabinMaterial)
    bowMesh.rotation.z = Math.PI / 2
    bowMesh.position.set(shipLength / 2 - 2, shipHeight * 0.5, 0)
    bowMesh.castShadow = true
    this.shipGroup.add(bowMesh)

    const cabinGeometry = new THREE.BoxGeometry(12, 3.5, shipWidth * 0.75)
    const cabinMesh = new THREE.Mesh(cabinGeometry, cabinMaterial)
    cabinMesh.position.set(-5, shipHeight * 0.3 + 0.3 + 1.75, 0)
    cabinMesh.castShadow = true
    cabinMesh.receiveShadow = true
    this.shipGroup.add(cabinMesh)

    const roofGeometry = new THREE.BoxGeometry(13, 0.4, shipWidth * 0.85)
    const roofMesh = new THREE.Mesh(roofGeometry, cabinMaterial)
    roofMesh.position.set(-5, shipHeight * 0.3 + 0.3 + 3.5 + 0.2, 0)
    roofMesh.castShadow = true
    this.shipGroup.add(roofMesh)
  }

  _createMastsAndSails(shipLength, shipWidth, shipHeight) {
    const mastGeometry = new THREE.CylinderGeometry(0.2, 0.3, 18, 8)
    const mastMaterial = new THREE.MeshStandardMaterial({ color: 0x3d2817, roughness: 0.9 })

    const mast1 = new THREE.Mesh(mastGeometry, mastMaterial)
    mast1.position.set(8, shipHeight * 0.3 + 0.3 + 9, 0)
    mast1.castShadow = true
    this.shipGroup.add(mast1)

    const mast2 = new THREE.Mesh(mastGeometry, mastMaterial)
    mast2.position.set(-12, shipHeight * 0.3 + 0.3 + 9, 0)
    mast2.castShadow = true
    this.shipGroup.add(mast2)

    const sailGeometry = new THREE.PlaneGeometry(10, 14)
    const sailMaterial = new THREE.MeshStandardMaterial({
      color: 0xf5f5dc, roughness: 0.9, side: THREE.DoubleSide,
      transparent: true, opacity: 0.9
    })

    const sail1 = new THREE.Mesh(sailGeometry, sailMaterial)
    sail1.position.set(8, shipHeight * 0.3 + 0.3 + 9, 0.1)
    sail1.rotation.y = 0.2
    this.shipGroup.add(sail1)

    const sail2 = new THREE.Mesh(sailGeometry, sailMaterial)
    sail2.position.set(-12, shipHeight * 0.3 + 0.3 + 9, 0.1)
    sail2.rotation.y = -0.15
    this.shipGroup.add(sail2)
  }

  _createRailings(shipLength, shipWidth, shipHeight) {
    const railingMaterial = new THREE.MeshStandardMaterial({ color: 0x2d1810, roughness: 0.8 })

    for (let i = 0; i < 4; i++) {
      const postGeometry = new THREE.CylinderGeometry(0.08, 0.08, 1.2, 6)
      const post1 = new THREE.Mesh(postGeometry, railingMaterial)
      post1.position.set(-shipLength / 2 + 5 + i * 10, shipHeight * 0.3 + 0.3 + 0.6, shipWidth * 0.4)
      post1.castShadow = true
      this.shipGroup.add(post1)

      const post2 = new THREE.Mesh(postGeometry, railingMaterial)
      post2.position.set(-shipLength / 2 + 5 + i * 10, shipHeight * 0.3 + 0.3 + 0.6, -shipWidth * 0.4)
      post2.castShadow = true
      this.shipGroup.add(post2)
    }

    const railGeometry = new THREE.BoxGeometry(shipLength * 0.8, 0.06, 0.06)
    const rail1 = new THREE.Mesh(railGeometry, railingMaterial)
    rail1.position.set(0, shipHeight * 0.3 + 0.3 + 1.0, shipWidth * 0.4)
    this.shipGroup.add(rail1)

    const rail2 = new THREE.Mesh(railGeometry, railingMaterial)
    rail2.position.set(0, shipHeight * 0.3 + 0.3 + 1.0, -shipWidth * 0.4)
    this.shipGroup.add(rail2)
  }

  _createCargoHolds(shipLength, shipWidth, shipHeight) {
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
        color: 0x3d2817, roughness: 0.8,
        transparent: true, opacity: 0.3, wireframe: false
      })

      const holdMesh = new THREE.Mesh(holdGeometry, holdMaterial)
      holdMesh.position.set(pos.x, shipHeight * 0.3 + 0.15 - holdDepth / 2, 0)
      holdMesh.userData = { holdId: this._cargoHolds[index]?.id, holdName: pos.name, holdIndex: index }
      holdMesh.receiveShadow = true
      this.shipGroup.add(holdMesh)
      this.holdMeshes.push(holdMesh)

      const edgeGeometry = new THREE.EdgesGeometry(holdGeometry)
      const edgeMaterial = new THREE.LineBasicMaterial({ color: 0x409EFF, linewidth: 2 })
      const edges = new THREE.LineSegments(edgeGeometry, edgeMaterial)
      edges.position.copy(holdMesh.position)
      this.shipGroup.add(edges)
    })
  }

  updateCargoBlocks(cargoLoadings, cargoHolds) {
    if (cargoHolds) this._cargoHolds = cargoHolds
    if (cargoLoadings) this._cargoLoadings = cargoLoadings

    this.cargoMeshes.forEach(mesh => this.shipGroup.remove(mesh))
    this.cargoMeshes = []

    if (!this._cargoLoadings || this._cargoLoadings.length === 0) return

    const shipHeight = 6
    const holdDepth = shipHeight * 0.5
    const holdPositions = [15, 5, -5, -15]
    const holdWidth = 12 * 0.7
    const holdLength = 8

    this._cargoLoadings.forEach(loading => {
      const holdIndex = this._cargoHolds.findIndex(h => h.id === loading.cargoHoldId || h.id === loading.holdId)
      if (holdIndex === -1) return

      const holdX = holdPositions[holdIndex]
      const maxWeight = this._cargoHolds[holdIndex]?.maxWeight || 100
      const fillRatio = Math.min((loading.weight || 0) / maxWeight, 1)
      const blockHeight = (holdDepth - 0.2) * fillRatio

      if (blockHeight <= 0.1) return

      const cargoColor = loading.cargoColor || loading.colorCode || '#409EFF'
      const color = new THREE.Color(cargoColor)

      const cargoGeometry = new THREE.BoxGeometry(holdLength - 0.4, blockHeight, holdWidth - 0.4)
      const cargoMaterial = new THREE.MeshStandardMaterial({
        color: color, roughness: 0.7, metalness: 0.1
      })

      const cargoMesh = new THREE.Mesh(cargoGeometry, cargoMaterial)
      cargoMesh.position.set(holdX, shipHeight * 0.3 + 0.15 - holdDepth + blockHeight / 2 + 0.1, 0)
      cargoMesh.castShadow = true
      cargoMesh.receiveShadow = true
      cargoMesh.userData = {
        cargoName: loading.cargoTypeName || loading.typeName,
        weight: loading.weight,
        holdName: this._cargoHolds[holdIndex]?.holdName
      }
      this.shipGroup.add(cargoMesh)
      this.cargoMeshes.push(cargoMesh)
    })
  }

  setViewMode(mode) {
    this._viewMode = mode
    this.controls.reset()

    const shipCenter = new THREE.Vector3(0, 2, 0)
    switch (mode) {
      case 'perspective':
        this.camera.position.set(60, 40, 60); break
      case 'front':
        this.camera.position.set(80, 15, 0); break
      case 'side':
        this.camera.position.set(0, 15, 80); break
      case 'top':
        this.camera.position.set(0, 100, 0.01); break
    }
    this.controls.target.copy(shipCenter)
    this.controls.update()
  }

  setShowWireframe(value) {
    this._showWireframe = value
  }

  setShowWater(value) {
    this._showWater = value
  }

  setAutoRotate(value) {
    this._autoRotate = value
  }

  setSensorData(sensorData) {
    this._sensorData = sensorData
  }

  setOnHoldHover(callback) {
    this._onHoldHover = callback
  }

  _onResize() {
    if (!this.container) return
    const containerRect = this.container.getBoundingClientRect()
    this.camera.aspect = containerRect.width / containerRect.height
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(containerRect.width, containerRect.height)
  }

  _onMouseMove(event) {
    if (!this.canvas) return
    const rect = this.canvas.getBoundingClientRect()
    this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

    this.raycaster.setFromCamera(this.mouse, this.camera)
    const intersects = this.raycaster.intersectObjects([...this.holdMeshes, ...this.cargoMeshes])

    if (intersects.length > 0) {
      const obj = intersects[0].object
      let hoverInfo = null
      if (obj.userData.holdId) {
        const hold = this._cargoHolds.find(h => h.id === obj.userData.holdId)
        hoverInfo = { ...hold, holdName: obj.userData.holdName }
      } else if (obj.userData.cargoName) {
        hoverInfo = {
          holdName: obj.userData.holdName,
          cargoName: obj.userData.cargoName,
          weight: obj.userData.weight
        }
      }
      if (this._onHoldHover) this._onHoldHover(hoverInfo)
      if (typeof document !== 'undefined') document.body.style.cursor = 'pointer'
    } else {
      if (this._onHoldHover) this._onHoldHover(null)
      if (typeof document !== 'undefined') document.body.style.cursor = 'grab'
    }
  }

  _animate() {
    this.animationId = requestAnimationFrame(this._animate)

    const elapsedTime = this.clock.getElapsedTime()

    if (this.waterMesh && this.waterMesh.material.uniforms) {
      this.waterMesh.material.uniforms.time.value = elapsedTime
    }

    if (this.shipGroup) {
      const rollAngle = this._sensorData?.rollAngle || 0
      const pitchAngle = this._sensorData?.pitchAngle || 0
      const targetRoll = (rollAngle * Math.PI) / 180
      const targetPitch = (pitchAngle * Math.PI) / 180
      const waveOffset = Math.sin(elapsedTime * 0.5) * 0.02
      const waveSway = Math.sin(elapsedTime * 0.3) * 0.01

      this.shipGroup.rotation.z += (targetRoll + waveSway - this.shipGroup.rotation.z) * 0.05
      this.shipGroup.rotation.x += (targetPitch + waveOffset - this.shipGroup.rotation.x) * 0.05
      this.shipGroup.position.y = -1 + Math.sin(elapsedTime * 0.8) * 0.1
    }

    if (this.controls) {
      this.controls.autoRotate = !!this._autoRotate
      this.controls.autoRotateSpeed = 0.5
    }

    const updateWireframe = (meshes) => {
      meshes.forEach(mesh => {
        if (mesh.material) mesh.material.wireframe = !!this._showWireframe
      })
    }
    updateWireframe(this.holdMeshes)
    updateWireframe(this.cargoMeshes)

    if (this.waterMesh) {
      this.waterMesh.visible = !!this._showWater
    }

    this.controls.update()
    this.renderer.render(this.scene, this.camera)
  }

  dispose() {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId)
      this.animationId = null
    }

    window.removeEventListener('resize', this._onResize)
    if (this.canvas) {
      this.canvas.removeEventListener('mousemove', this._onMouseMove)
    }

    const disposeObject = (obj) => {
      if (obj.geometry) obj.geometry.dispose()
      if (obj.material) {
        if (Array.isArray(obj.material)) {
          obj.material.forEach(m => m.dispose())
        } else {
          obj.material.dispose()
        }
      }
    }

    if (this.scene) {
      this.scene.traverse(disposeObject)
    }

    if (this.renderer) {
      this.renderer.dispose()
    }

    this.scene = null
    this.camera = null
    this.renderer = null
    this.controls = null
    this.shipGroup = null
    this.waterMesh = null
    this.cargoMeshes = []
    this.holdMeshes = []
  }
}

export default JunkShip3D
