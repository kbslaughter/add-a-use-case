<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:">
      <template v-if="studentBeingEdited.enabled !== undefined">
        <el-tag v-if="studentBeingEdited.enabled" type="success" data-cy="student-status"
          >Active</el-tag
        >
        <el-tag v-else type="danger" data-cy="student-status">Deactivated</el-tag>
      </template>
      <el-button
        v-if="studentBeingEdited.enabled"
        type="danger"
        plain
        size="small"
        style="margin-left: 12px"
        :loading="isDeactivating"
        data-cy="student-deactivate"
        @click="confirmDeactivation()"
      >
        Deactivate
      </el-button>
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: undefined, // Unknown until the student loads, so neither a status nor the action shows before then
  firstName: '',
  lastName: '',
  email: ''
})

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    enabled: studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}

const isDeactivating = ref(false)

// UC-STU-deactivate-student: warn about the consequences (step 4), and deactivate only once confirmed (step 5)
async function confirmDeactivation() {
  try {
    await ElMessageBox.confirm(
      'This student will no longer be able to log in or submit weekly activity reports and peer evaluations. Her account and submitted work are kept, and the account can be reactivated.',
      'Deactivate this student?',
      { confirmButtonText: 'Deactivate', cancelButtonText: 'Cancel', type: 'warning' }
    )
  } catch {
    return // Cancelled at the warning (4a): nothing is sent
  }

  isDeactivating.value = true
  try {
    await deactivateStudent(studentId)
    ElMessage.success('This student has been deactivated.')
    emit('close-dialog') // Reloads the list, and discards any unsaved edits in this form
  } catch (error) {
    // The dialog was stale and she is already deactivated (3a). The shared toast has told the user;
    // reloading her status removes the action.
    if (axios.isAxiosError(error) && error.response?.status === 400) {
      await loadStudent(studentId)
    }
  } finally {
    isDeactivating.value = false
  }
}
</script>

<style scoped lang="scss"></style>
