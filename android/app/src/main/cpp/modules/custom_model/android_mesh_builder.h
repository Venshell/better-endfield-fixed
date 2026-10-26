#pragma once
#include "core/runtime.h"
#include "bem.h"
#include <vector>
#include <array>
#include <cstdint>

namespace betterendfield {
// Private, same-binary platform boundary; does not extend BE_HostApiV1.
void ConfigureAndroidMeshBuilder(Il2CppRuntime& runtime, bool rollback_test = false, bool pipeline_lod = false, bool npc_parameters = false, bool inspect = false);
void ConfigureHeadwearCanary(Il2CppRuntime& runtime);
void AndroidWarmHeadwearLayout();
bool AndroidMeshRollbackTest();
bool AndroidPipelineLodEnabled();
bool AndroidNpcParametersEnabled();
bool AndroidInspectionEnabled();
std::string AndroidTextureMipRequestToken();
void* AndroidLodStreamingOffsetEntry();
void* AndroidTextureBudgetSetterEntry();
void* AndroidTextureBudgetGetterEntry();
void* AndroidQualityLevelSetterEntry();
void* AndroidQualityLevelGetterEntry();
bool AndroidReadMemoryHeadroom(uint64_t& total_bytes,uint64_t& available_bytes);
void AndroidAuditNormalTexture(void* texture,const std::string& name);
bool AndroidAuditMaterialCopy(void* original, void* copy);
void AndroidAuditTextureColorSpace(void* original, void* replacement, const std::string& name);
bool AndroidMeshBuilderReady();
bool AndroidReadMeshStrides(void* mesh, std::vector<int32_t>& strides);
// Descriptor fields: topology/indexStart/indexCount/baseVertex. Metadata only;
// never reads discarded source CPU vertex or index buffer contents.
bool AndroidReadMeshSubmeshes(void* mesh, std::vector<std::array<int64_t,4>>& submeshes);
// Caller owns and roots the unpublished Mesh and initializes its skin field.
bool AndroidSubmitMesh(void* mesh, const BetterEndfield::CustomModel::BemComponent& component);
// Bounded game-version catalog; applies owned visible meshes and restores
// only references held by this session. No source Mesh cloning or writes.
bool AndroidHeadwearFixtureAvailable(void* renderer, void* source_mesh);
void AndroidPruneHeadwearFixtures(void* const* renderers, size_t count);
bool AndroidProbeHeadwearFixture(void* renderer, void* source_mesh);
bool AndroidHeadwearFixtureOwns(void* renderer, void* mesh);
bool AndroidRestoreHeadwearFixture();
void AndroidDiscardHeadwearFixtures();
// Retains the game's resource handle until Release; does not instantiate or
// display the donor prefab. Only called inside a main-thread delivery scope.
void* AndroidLoadUiDonor(const std::string& resource, void*& handle, uint32_t& root);
void AndroidReleaseUiDonor(void* handle, uint32_t root);
}
